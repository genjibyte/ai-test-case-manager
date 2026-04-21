package com.alsystem.casemanager.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.alsystem.casemanager.dto.AiChatMessageDTO;
import com.alsystem.casemanager.dto.AiChatRequestDTO;
import com.alsystem.casemanager.entity.AiChatSession;
import com.alsystem.casemanager.mapper.AiChatSessionMapper;
import com.alsystem.casemanager.service.AiChatSessionService;
import com.alsystem.casemanager.service.ProjectService;
import com.alsystem.casemanager.vo.AiChatResponseVO;
import com.alsystem.casemanager.vo.AiChatSessionDetailVO;
import com.alsystem.casemanager.vo.AiChatSessionListVO;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiChatSessionServiceImpl implements AiChatSessionService {

  private final AiChatSessionMapper aiChatSessionMapper;
  private final ObjectMapper objectMapper;
  private final ProjectService projectService;

  /**
   * 构造函数：注入会话 Mapper、JSON 工具与项目服务。
   */
  public AiChatSessionServiceImpl(
      AiChatSessionMapper aiChatSessionMapper,
      ObjectMapper objectMapper,
      ProjectService projectService) {
    this.aiChatSessionMapper = aiChatSessionMapper;
    this.objectMapper = objectMapper;
    this.projectService = projectService;
  }

  @Override
  /**
   * 创建空会话。
   */
  public String createEmptySession(Long userId, Long projectId) {
    // 校验项目访问权限。
    projectService.detail(projectId);
    LocalDateTime now = LocalDateTime.now();
    // 初始化会话主记录。
    AiChatSession s = new AiChatSession();
    s.setSessionKey(UUID.randomUUID().toString());
    s.setUserId(userId);
    s.setProjectId(projectId);
    s.setTitle("新会话");
    try {
      // 初始化为空消息数组。
      s.setMessagesJson(objectMapper.writeValueAsString(objectMapper.createArrayNode()));
    } catch (Exception e) {
      s.setMessagesJson("[]");
    }
    s.setCreateTime(now);
    s.setUpdateTime(now);
    // 入库并返回会话键。
    aiChatSessionMapper.insert(s);
    return s.getSessionKey();
  }

  @Override
  /**
   * 查询会话列表（按更新时间倒序）。
   */
  public List<AiChatSessionListVO> listSessions(Long userId, Long projectId) {
    // 先校验项目访问权限。
    projectService.detail(projectId);
    List<AiChatSession> rows =
        aiChatSessionMapper.selectList(
            Wrappers.<AiChatSession>lambdaQuery()
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getProjectId, projectId)
                .orderByDesc(AiChatSession::getUpdateTime));
    // 实体转列表 VO。
    return rows.stream()
        .map(
            r -> {
              AiChatSessionListVO vo = new AiChatSessionListVO();
              vo.setSessionId(r.getSessionKey());
              vo.setTitle(r.getTitle() != null ? r.getTitle() : "会话");
              vo.setUpdateTime(r.getUpdateTime());
              return vo;
            })
        .collect(Collectors.toList());
  }

  @Override
  /**
   * 查询会话详情。
   */
  public AiChatSessionDetailVO getSessionDetail(Long userId, Long projectId, String sessionKey) {
    projectService.detail(projectId);
    AiChatSession s = loadAndCheck(sessionKey, userId, projectId);
    AiChatSessionDetailVO vo = new AiChatSessionDetailVO();
    vo.setSessionId(s.getSessionKey());
    vo.setTitle(s.getTitle());
    vo.setUpdateTime(s.getUpdateTime());
    vo.setWorkflowContextJson(s.getWorkflowContextJson());
    // 读取并反序列化消息 JSON。
    try {
      if (StringUtils.hasText(s.getMessagesJson())) {
        vo.setMessages(objectMapper.readTree(s.getMessagesJson()));
      } else {
        vo.setMessages(objectMapper.createArrayNode());
      }
    } catch (Exception e) {
      vo.setMessages(objectMapper.createArrayNode());
    }
    return vo;
  }

  @Override
  /**
   * workflow 完成后持久化会话数据。
   */
  public String persistAfterWorkflow(AiChatRequestDTO dto, AiChatResponseVO vo, Long userId) {
    long projectId = Long.parseLong(dto.getProjectId().trim());
    projectService.detail(projectId);
    // 构建消息快照与工作流上下文 JSON。
    String messagesJson = buildMessagesJsonAfterWorkflow(dto, vo.getReply());
    String wfCtx = buildWorkflowContextJson(vo);
    String title = inferTitle(dto);
    LocalDateTime now = LocalDateTime.now();
    if (!StringUtils.hasText(dto.getSessionId())) {
      // 无会话 ID：创建新会话。
      AiChatSession s = new AiChatSession();
      s.setSessionKey(UUID.randomUUID().toString());
      s.setUserId(userId);
      s.setProjectId(projectId);
      s.setTitle(title);
      s.setMessagesJson(messagesJson);
      s.setWorkflowContextJson(wfCtx);
      s.setCreateTime(now);
      s.setUpdateTime(now);
      aiChatSessionMapper.insert(s);
      return s.getSessionKey();
    }
    // 有会话 ID：更新已有会话。
    AiChatSession s = loadAndCheck(dto.getSessionId().trim(), userId, projectId);
    s.setTitle(title);
    s.setMessagesJson(messagesJson);
    s.setWorkflowContextJson(wfCtx);
    s.setUpdateTime(now);
    aiChatSessionMapper.updateById(s);
    return s.getSessionKey();
  }

  @Override
  /**
   * agent 完成后持久化会话数据。
   */
  public String persistAfterAgent(
      AiChatRequestDTO dto, String assistantReply, String cozeConversationId, Long userId) {
    long projectId = Long.parseLong(dto.getProjectId().trim());
    projectService.detail(projectId);
    String reply = assistantReply != null ? assistantReply : "";
    String title = inferTitle(dto);
    LocalDateTime now = LocalDateTime.now();
    if (!StringUtils.hasText(dto.getSessionId())) {
      // 新会话：直接构建全量消息并插入。
      String messagesJson = buildMessagesJsonAfterAgent(dto, reply);
      AiChatSession s = new AiChatSession();
      s.setSessionKey(UUID.randomUUID().toString());
      s.setUserId(userId);
      s.setProjectId(projectId);
      s.setTitle(title);
      s.setMessagesJson(messagesJson);
      s.setCreateTime(now);
      s.setUpdateTime(now);
      if (StringUtils.hasText(cozeConversationId)) {
        s.setCozeConversationId(cozeConversationId.trim());
      }
      aiChatSessionMapper.insert(s);
      return s.getSessionKey();
    }
    // 已有会话：在历史上合并本轮 assistant 回复。
    AiChatSession s = loadAndCheck(dto.getSessionId().trim(), userId, projectId);
    String messagesJson = mergeAgentMessagesJson(s.getMessagesJson(), dto, reply);
    s.setTitle(title);
    s.setMessagesJson(messagesJson);
    s.setUpdateTime(now);
    if (StringUtils.hasText(cozeConversationId)) {
      s.setCozeConversationId(cozeConversationId.trim());
    }
    aiChatSessionMapper.updateById(s);
    return s.getSessionKey();
  }

  /**
   * 以本轮 dto+助手回复为主；若库中消息条数更多（客户端未带全量历史），在库记录末尾追加本轮助手，避免覆盖掉
   * 已保存的 Agent 输出。
   */
  private String mergeAgentMessagesJson(
      String existingJson, AiChatRequestDTO dto, String assistantReply) {
    String built = buildMessagesJsonAfterAgent(dto, assistantReply);
    if (!StringUtils.hasText(existingJson)) {
      return built;
    }
    try {
      ArrayNode b = (ArrayNode) objectMapper.readTree(built);
      ArrayNode e = (ArrayNode) objectMapper.readTree(existingJson);
      if (b.size() >= e.size()) {
        return built;
      }
      ArrayNode out = objectMapper.createArrayNode();
      for (int i = 0; i < e.size(); i++) {
        out.add(e.get(i));
      }
      JsonNode last = out.get(out.size() - 1);
      if (last != null
          && last.has("role")
          && "user".equalsIgnoreCase(last.get("role").asText())
          && StringUtils.hasText(assistantReply)) {
        ObjectNode asst = objectMapper.createObjectNode();
        asst.put("role", "assistant");
        asst.put("content", assistantReply);
        out.add(asst);
        return objectMapper.writeValueAsString(out);
      }
      return existingJson;
    } catch (Exception ex) {
      return built;
    }
  }

  @Override
  /**
   * 查询会话绑定的 Coze conversation_id。
   */
  public String getCozeConversationId(Long userId, Long projectId, String sessionKey) {
    if (!StringUtils.hasText(sessionKey)) {
      return null;
    }
    AiChatSession s = loadAndCheck(sessionKey.trim(), userId, projectId);
    return s.getCozeConversationId();
  }

  private AiChatSession loadAndCheck(String sessionKey, Long userId, long projectId) {
    // 会话归属校验：sessionKey + userId + projectId 三要素必须同时匹配。
    AiChatSession s =
        aiChatSessionMapper.selectOne(
            Wrappers.<AiChatSession>lambdaQuery()
                .eq(AiChatSession::getSessionKey, sessionKey)
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getProjectId, projectId));
    if (s == null) {
      throw new IllegalArgumentException("会话不存在或无权访问");
    }
    return s;
  }

  /**
   * 推断会话标题：优先首条 user 内容。
   */
  private String inferTitle(AiChatRequestDTO dto) {
    if (dto.getMessages() == null) {
      return "新会话";
    }
    for (AiChatMessageDTO m : dto.getMessages()) {
      if (m != null
          && m.getRole() != null
          && "user".equalsIgnoreCase(m.getRole().trim())
          && StringUtils.hasText(m.getContent())) {
        String c = m.getContent().trim();
        return c.length() > 80 ? c.substring(0, 80) + "…" : c;
      }
    }
    return "新会话";
  }

  /**
   * 构建 workflow 模式的消息快照 JSON。
   */
  private String buildMessagesJsonAfterWorkflow(AiChatRequestDTO dto, String assistantReply) {
    try {
      ArrayNode arr = objectMapper.createArrayNode();
      // 先写入请求中的历史消息。
      if (dto.getMessages() != null) {
        for (AiChatMessageDTO m : dto.getMessages()) {
          if (m == null || !StringUtils.hasText(m.getRole())) {
            continue;
          }
          ObjectNode row = objectMapper.createObjectNode();
          row.put("role", m.getRole().toLowerCase());
          row.put("content", m.getContent() != null ? m.getContent() : "");
          arr.add(row);
        }
      }
      // 再追加本轮 assistant 结果。
      ObjectNode asst = objectMapper.createObjectNode();
      asst.put("role", "assistant");
      asst.put("content", assistantReply != null ? assistantReply : "");
      arr.add(asst);
      return objectMapper.writeValueAsString(arr);
    } catch (Exception e) {
      throw new IllegalArgumentException("序列化消息失败：" + e.getMessage());
    }
  }

  /**
   * 构建 workflow 上下文 JSON。
   */
  private String buildWorkflowContextJson(AiChatResponseVO vo) {
    try {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("workflowReply", vo.getReply() != null ? vo.getReply() : "");
      root.set(
          "cases",
          objectMapper.valueToTree(vo.getCases() != null ? vo.getCases() : Collections.emptyList()));
      return objectMapper.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalArgumentException("序列化工作流上下文失败：" + e.getMessage());
    }
  }

  /**
   * 构建 agent 模式的消息快照 JSON。
   */
  private String buildMessagesJsonAfterAgent(AiChatRequestDTO dto, String assistantReply) {
    try {
      ArrayNode arr = objectMapper.createArrayNode();
      if (dto.getMessages() != null) {
        for (AiChatMessageDTO m : dto.getMessages()) {
          if (m == null || !StringUtils.hasText(m.getRole())) {
            continue;
          }
          ObjectNode row = objectMapper.createObjectNode();
          row.put("role", m.getRole().toLowerCase());
          row.put("content", m.getContent() != null ? m.getContent() : "");
          arr.add(row);
        }
      }
      ObjectNode asst = objectMapper.createObjectNode();
      asst.put("role", "assistant");
      asst.put("content", assistantReply != null ? assistantReply : "");
      arr.add(asst);
      return objectMapper.writeValueAsString(arr);
    } catch (Exception e) {
      throw new IllegalArgumentException("序列化消息失败：" + e.getMessage());
    }
  }
}
