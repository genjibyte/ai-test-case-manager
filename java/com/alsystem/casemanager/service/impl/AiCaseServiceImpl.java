package com.alsystem.casemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.alsystem.casemanager.coze.CozeContentExtractor;
import com.alsystem.casemanager.coze.CozeConversationIdExtractor;
import com.alsystem.casemanager.coze.CozeHttpUtils;
import com.alsystem.casemanager.coze.CozeResponseParser;
import com.alsystem.casemanager.coze.CozeSseLineParser;
import com.alsystem.casemanager.dto.AiChatMessageDTO;
import com.alsystem.casemanager.dto.AiChatRequestDTO;
import com.alsystem.casemanager.service.AiCaseService;
import com.alsystem.casemanager.service.AiChatSessionService;
import com.alsystem.casemanager.service.ProjectService;
import com.alsystem.casemanager.util.SecurityUtil;
import com.alsystem.casemanager.vo.AiChatResponseVO;
import com.alsystem.casemanager.vo.AiGeneratedCaseVO;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiCaseServiceImpl implements AiCaseService {

  private static final Logger log = LoggerFactory.getLogger(AiCaseServiceImpl.class);

  private final ProjectService projectService;
  private final SecurityUtil securityUtil;
  private final ObjectMapper objectMapper;
  private final Executor aiExecutor;
  private final AiChatSessionService aiChatSessionService;

  private final String workflowUrl;
  private final String agentUrl;
  private final String apiKey;
  private final String apiKeyHeaderName;
  private final String cozeWorkflowId;
  private final String cozeBotId;

  /**
   * 构造函数：注入 AI 调用所需依赖和配置。
   */
  public AiCaseServiceImpl(
      ProjectService projectService,
      SecurityUtil securityUtil,
      ObjectMapper objectMapper,
      AiChatSessionService aiChatSessionService,
      @Qualifier("aiTaskExecutor") Executor aiExecutor,
      @Value("${alsystem.ai.workflow-url:https://api.coze.cn/v1/workflow/stream_run}") String workflowUrl,
      @Value("${alsystem.ai.agent-url:https://api.coze.cn/v3/chat}") String agentUrl,
      @Value("${alsystem.ai.api-key:}") String apiKey,
      @Value("${alsystem.ai.api-key-header-name:Authorization}") String apiKeyHeaderName,
      @Value("${alsystem.ai.coze-workflow-id:7621188142721400838}") String cozeWorkflowId,
      @Value("${alsystem.ai.coze-bot-id:7621194831741943848}") String cozeBotId) {
    this.projectService = projectService;
    this.securityUtil = securityUtil;
    this.objectMapper = objectMapper;
    this.aiChatSessionService = aiChatSessionService;
    this.aiExecutor = aiExecutor;
    this.workflowUrl = workflowUrl;
    this.agentUrl = agentUrl;
    this.apiKey = apiKey;
    this.apiKeyHeaderName = apiKeyHeaderName;
    this.cozeWorkflowId = cozeWorkflowId;
    this.cozeBotId = cozeBotId;
  }

  @Override
  /**
   * workflow 同步接口：调用 Coze 工作流并聚合响应。
   */
  public AiChatResponseVO chat(AiChatRequestDTO dto) {
    // 项目权限校验。
    long projectId = Long.parseLong(dto.getProjectId().trim());
    projectService.detail(projectId);

    // 校验模式正确，避免误调。
    String mode = dto.getMode().trim().toLowerCase();
    if (!"workflow".equals(mode)) {
      throw new IllegalArgumentException("请使用 POST /api/case/ai/chat/stream 调用 Agent 流式接口");
    }
    // 校验关键配置存在。
    if (!StringUtils.hasText(workflowUrl)) {
      throw new IllegalArgumentException("未配置工作流地址：alsystem.ai.workflow-url");
    }
    if (!StringUtils.hasText(cozeWorkflowId)) {
      throw new IllegalArgumentException("未配置 Coze 工作流 ID：alsystem.ai.coze-workflow-id");
    }

    // 构建上游请求体。
    String bodyJson = buildCozeWorkflowPayload(dto);
    try {
      // 发起 HTTP 请求到 Coze 工作流接口。
      HttpURLConnection conn =
          CozeHttpUtils.openPostJson(
              workflowUrl, bodyJson, apiKey, apiKeyHeaderName, CozeHttpUtils.READ_TIMEOUT_WORKFLOW_MS);
      int code = conn.getResponseCode();
      InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
      if (code < 200 || code >= 300) {
        // 上游失败时透传错误信息便于排查。
        String err = CozeHttpUtils.readUtf8Stream(in);
        throw new IllegalArgumentException("Coze 工作流失败 HTTP " + code + "：" + err);
      }
      String ct = conn.getHeaderField("Content-Type");
      if (ct != null && ct.toLowerCase().contains("text/event-stream")) {
        // SSE 场景：聚合 delta 后返回完整 VO。
        AiChatResponseVO vo = aggregateWorkflowStream(in, dto.getProjectId());
        // 尝试落库会话，不影响主结果返回。
        finishWorkflowSession(dto, vo);
        return vo;
      }
      // 普通 JSON 场景。
      String raw = CozeHttpUtils.readUtf8Stream(in);
      if (raw == null || raw.isEmpty()) {
        AiChatResponseVO out = new AiChatResponseVO();
        out.setReply("（上游返回空响应）");
        finishWorkflowSession(dto, out);
        return out;
      }
      AiChatResponseVO vo = CozeResponseParser.parseJsonBody(raw, dto.getProjectId(), objectMapper);
      finishWorkflowSession(dto, vo);
      return vo;
    } catch (IOException e) {
      throw new IllegalArgumentException("调用 Coze 工作流失败：" + e.getMessage());
    }
  }

  @Override
  /**
   * agent 流式接口：透传上游 SSE 到前端。
   */
  public void chatStream(AiChatRequestDTO dto, SseEmitter emitter) {
    long projectId = Long.parseLong(dto.getProjectId().trim());
    projectService.detail(projectId);

    String mode = dto.getMode().trim().toLowerCase();
    if (!"agent".equals(mode)) {
      emitter.completeWithError(new IllegalArgumentException("流式接口仅支持 agent"));
      return;
    }
    if (!StringUtils.hasText(agentUrl)) {
      emitter.completeWithError(new IllegalArgumentException("未配置 Agent 地址：alsystem.ai.agent-url"));
      return;
    }
    if (!StringUtils.hasText(cozeBotId)) {
      emitter.completeWithError(new IllegalArgumentException("未配置 Coze Bot ID：alsystem.ai.coze-bot-id"));
      return;
    }

    // 读取上下文用户 ID，作为上游 user_id。
    final long cozeUserId = securityUtil.currentUserId();

    // 通过异步线程执行，避免阻塞 Web 线程。
    aiExecutor.execute(
        new DelegatingSecurityContextRunnable(
            () -> {
              try {
                // 构建 Agent 请求体。
                String bodyJson = buildCozeAgentPayload(dto, cozeUserId);
                HttpURLConnection conn =
                    CozeHttpUtils.openPostJson(agentUrl, bodyJson, apiKey, apiKeyHeaderName);
                int code = conn.getResponseCode();
                InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                if (code < 200 || code >= 300) {
                  // 上游失败，向前端推送 error 事件。
                  String err = CozeHttpUtils.readUtf8Stream(in);
                  sendError(emitter, "Coze Agent HTTP " + code + "：" + err);
                  emitter.complete();
                  return;
                }
                String ct = conn.getHeaderField("Content-Type");
                if (ct != null && ct.toLowerCase().contains("text/event-stream")) {
                  // 流式：边透传 delta，边聚合最终文本用于落库。
                  AgentStreamResult r = proxyAgentSse(in, emitter, dto.getProjectId());
                  String sid =
                      persistAgentSessionSafe(
                          dto, r.persistReplyText, r.cozeConversationId, cozeUserId);
                  sendDone(emitter, r.vo, sid);
                } else {
                  // 非流式：直接解析完整 JSON 响应。
                  String raw = CozeHttpUtils.readUtf8Stream(in);
                  AiChatResponseVO vo =
                      CozeResponseParser.parseJsonBody(raw, dto.getProjectId(), objectMapper);
                  String sid = persistAgentSessionSafe(dto, vo.getReply(), null, cozeUserId);
                  sendDone(emitter, vo, sid);
                }
                emitter.complete();
              } catch (Exception e) {
                try {
                  sendError(
                      emitter, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                } catch (IOException ignored) {
                  // ignore
                }
                emitter.complete();
              }
            }));
  }

  /**
   * 聚合工作流 SSE 响应为完整 VO。
   */
  private AiChatResponseVO aggregateWorkflowStream(InputStream in, String projectIdStr) throws IOException {
    StringBuilder accumulated = new StringBuilder();
    List<JsonNode> dataNodes = new ArrayList<>();
    // 遍历每一条 data 负载并累积文本。
    CozeSseLineParser.forEachDataPayload(
        in,
        payload -> {
          try {
            JsonNode node = objectMapper.readTree(payload);
            dataNodes.add(node);
            // 提取 delta 文本并拼接。
            String delta = CozeContentExtractor.extractDeltaText(node);
            if (delta != null) {
              accumulated.append(delta);
            }
          } catch (Exception ignored) {
            // 单行非 JSON 时忽略
          }
        });
    return CozeResponseParser.buildFromStream(accumulated, dataNodes, projectIdStr, objectMapper);
  }

  private static final class AgentStreamResult {
    final AiChatResponseVO vo;
    /** 扣子返回的会话 ID，用于后续轮次 */
    final String cozeConversationId;
    /** 与流式累计一致，用于落库（避免仅用 parse 结果与 SSE 不一致） */
    final String persistReplyText;

    AgentStreamResult(AiChatResponseVO vo, String cozeConversationId, String persistReplyText) {
      this.vo = vo;
      this.cozeConversationId = cozeConversationId;
      this.persistReplyText = persistReplyText;
    }
  }

  /**
   * 代理 Agent 的 SSE 数据流并构建最终结果。
   */
  private AgentStreamResult proxyAgentSse(InputStream in, SseEmitter emitter, String projectIdStr)
      throws IOException {
    AtomicReference<String> conv = new AtomicReference<>();
    StringBuilder accumulated = new StringBuilder();
    List<JsonNode> dataNodes = new ArrayList<>();
    // 逐条消费上游 data 事件。
    CozeSseLineParser.forEachDataPayload(
        in,
        payload -> {
          try {
            JsonNode node = objectMapper.readTree(payload);
            dataNodes.add(node);
            // 抽取上游 conversation_id，供后续多轮会话复用。
            String cid = CozeConversationIdExtractor.extract(node);
            if (cid != null && !cid.isEmpty()) {
              conv.set(cid);
            }
            String event = node.path("event").asText("");
            if ("conversation.message.completed".equals(event)) {
              // 完成事件优先使用全量文本，修正仅 delta 不完整的问题。
              String full = CozeContentExtractor.extractCompletedAnswerText(node);
              if (full != null && !full.isEmpty()) {
                int beforeLen = accumulated.length();
                accumulated.setLength(0);
                accumulated.append(full);
                if (beforeLen == 0) {
                  // 若前面没有 delta，补发一次完整 delta 给前端。
                  Map<String, Object> ev = new HashMap<>();
                  ev.put("type", "delta");
                  ev.put("text", full);
                  emitter.send(SseEmitter.event().data(ev));
                }
              }
              return;
            }
            String delta = CozeContentExtractor.extractDeltaText(node);
            if (delta != null && !delta.isEmpty()) {
              // 推送增量文本给前端实时渲染。
              accumulated.append(delta);
              Map<String, Object> ev = new HashMap<>();
              ev.put("type", "delta");
              ev.put("text", delta);
              emitter.send(SseEmitter.event().data(ev));
            }
          } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // 单行非 JSON
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }
        });
    // 从聚合数据构建 VO。
    AiChatResponseVO vo = CozeResponseParser.buildFromStream(accumulated, dataNodes, projectIdStr, objectMapper);
    String acc = accumulated.toString().trim();
    String reply = vo.getReply() != null ? vo.getReply().trim() : "";
    if ((!StringUtils.hasText(reply) || "（无文本内容）".equals(reply)) && StringUtils.hasText(acc)) {
      vo.setReply(acc);
    }
    // 优先使用流中累计文本作为落库内容，保持与前端观感一致。
    String persistReplyText = acc;
    if (!StringUtils.hasText(persistReplyText)) {
      persistReplyText = vo.getReply() != null ? vo.getReply().trim() : "";
    }
    if ("（无文本内容）".equals(persistReplyText)) {
      persistReplyText = "";
    }
    return new AgentStreamResult(vo, conv.get(), persistReplyText);
  }

  /**
   * 安全持久化 Agent 会话：失败仅记录日志，不影响主链路。
   */
  private String persistAgentSessionSafe(
      AiChatRequestDTO dto, String reply, String cozeConversationId, long userId) {
    try {
      return aiChatSessionService.persistAfterAgent(dto, reply, cozeConversationId, userId);
    } catch (Exception e) {
      log.warn("Agent 会话落库失败 projectId={} sessionId={}: {}", dto.getProjectId(), dto.getSessionId(), e.toString());
      return null;
    }
  }

  /**
   * workflow 结果落库，并把 sessionId 回填到返回 VO。
   */
  private void finishWorkflowSession(AiChatRequestDTO dto, AiChatResponseVO vo) {
    try {
      String sid = aiChatSessionService.persistAfterWorkflow(dto, vo, securityUtil.currentUserId());
      vo.setSessionId(sid);
    } catch (Exception ignored) {
      // 会话落库失败不影响工作流结果
    }
  }

  /**
   * 发送 done 事件，携带回复文本、会话 ID 与用例数组。
   */
  private void sendDone(SseEmitter emitter, AiChatResponseVO vo, String sessionId) throws IOException {
    Map<String, Object> done = new HashMap<>();
    done.put("type", "done");
    done.put("reply", vo.getReply() != null ? vo.getReply() : "");
    if (sessionId != null) {
      done.put("sessionId", sessionId);
    }
    // 展平用例对象，确保前端拿到稳定字段结构。
    List<Map<String, Object>> caseRows = new ArrayList<>();
    if (vo.getCases() != null) {
      for (AiGeneratedCaseVO c : vo.getCases()) {
        Map<String, Object> row = new HashMap<>();
        row.put("tempId", c.getTempId());
        row.put("projectId", c.getProjectId());
        row.put("caseName", c.getCaseName());
        row.put("caseCode", c.getCaseCode());
        row.put("caseLevel", c.getCaseLevel());
        row.put("caseStatus", c.getCaseStatus());
        row.put("mindMapJson", c.getMindMapJson());
        caseRows.add(row);
      }
    }
    done.put("cases", caseRows);
    emitter.send(SseEmitter.event().data(done));
  }

  /**
   * 发送 error 事件。
   */
  private void sendError(SseEmitter emitter, String message) throws IOException {
    Map<String, Object> err = new HashMap<>();
    err.put("type", "error");
    err.put("message", message);
    emitter.send(SseEmitter.event().data(err));
  }

  /**
   * 构建 Coze workflow 请求体。
   */
  private String buildCozeWorkflowPayload(AiChatRequestDTO dto) {
    try {
      // 构建根节点并设置 workflow_id。
      ObjectNode root = objectMapper.createObjectNode();
      root.put("workflow_id", cozeWorkflowId);
      // 将文档 URL / base64、场景、等级、用户追问映射到 parameters。
      ObjectNode params = objectMapper.createObjectNode();
      if (StringUtils.hasText(dto.getDocumentUrl())) {
        params.put("input", dto.getDocumentUrl().trim());
      }
      if (StringUtils.hasText(dto.getDocumentBase64())) {
        params.put("document_base64", dto.getDocumentBase64());
        params.put("document_name", dto.getDocumentName() != null ? dto.getDocumentName() : "document");
      }
      params.put("case_level", dto.getCaseLevel());
      ArrayNode scenes = objectMapper.createArrayNode();
      if (dto.getSceneType() != null) {
        for (Integer s : dto.getSceneType()) {
          if (s != null) {
            scenes.add(s);
          }
        }
      }
      params.set("scene_type", scenes);
      if (dto.getMessages() != null && !dto.getMessages().isEmpty()) {
        AiChatMessageDTO last = dto.getMessages().get(dto.getMessages().size() - 1);
        if (last != null
            && last.getRole() != null
            && "user".equalsIgnoreCase(last.getRole().trim())
            && StringUtils.hasText(last.getContent())) {
          // 仅取最后一条 user 消息作为本轮 prompt。
          params.put("user_prompt", last.getContent());
        }
      }
      root.set("parameters", params);
      return objectMapper.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalArgumentException("构建 Coze 工作流请求失败：" + e.getMessage());
    }
  }

  /**
   * 构建 Coze agent 请求体。
   */
  private String buildCozeAgentPayload(AiChatRequestDTO dto, long userId) {
    try {
      // 固定 bot_id / user_id / stream 参数。
      ObjectNode root = objectMapper.createObjectNode();
      root.put("bot_id", cozeBotId);
      root.put("user_id", String.valueOf(userId));
      root.put("stream", true);
      // 透传历史消息到 additional_messages。
      ArrayNode additional = objectMapper.createArrayNode();
      for (AiChatMessageDTO m : dto.getMessages()) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("content", m.getContent());
        msg.put("content_type", "text");
        boolean isAssistant = "assistant".equalsIgnoreCase(m.getRole());
        msg.put("role", isAssistant ? "assistant" : "user");
        msg.put("type", isAssistant ? "answer" : "question");
        additional.add(msg);
      }
      root.set("additional_messages", additional);
      // 构建业务参数（工作流上下文、文档信息、场景、等级）。
      ObjectNode parameters = objectMapper.createObjectNode();
      if (StringUtils.hasText(dto.getWorkflowContextJson())) {
        parameters.put("workflow_context", dto.getWorkflowContextJson());
      }
      if (StringUtils.hasText(dto.getDocumentBase64())) {
        parameters.put("document_base64", dto.getDocumentBase64());
        parameters.put("document_name", dto.getDocumentName() != null ? dto.getDocumentName() : "");
      }
      parameters.put("case_level", dto.getCaseLevel());
      ArrayNode scenes = objectMapper.createArrayNode();
      if (dto.getSceneType() != null) {
        for (Integer s : dto.getSceneType()) {
          if (s != null) {
            scenes.add(s);
          }
        }
      }
      parameters.set("scene_type", scenes);
      root.set("parameters", parameters);
      if (StringUtils.hasText(dto.getSessionId())) {
        // 若携带系统会话 ID，尝试取已落库的上游 conversation_id 做多轮续聊。
        String cid =
            aiChatSessionService.getCozeConversationId(
                userId,
                Long.parseLong(dto.getProjectId().trim()),
                dto.getSessionId().trim());
        if (StringUtils.hasText(cid)) {
          root.put("conversation_id", cid);
        }
      }
      return objectMapper.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalArgumentException("构建 Coze Agent 请求失败：" + e.getMessage());
    }
  }
}
