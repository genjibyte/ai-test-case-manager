package com.alsystem.casemanager.controller;

import com.alsystem.casemanager.annotation.OperationAuditLog;
import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.dto.AiChatRequestDTO;
import com.alsystem.casemanager.dto.AiSessionCreateDTO;
import com.alsystem.casemanager.dto.ConvertDto;
import com.alsystem.casemanager.service.AiCaseService;
import com.alsystem.casemanager.service.AiChatSessionService;
import com.alsystem.casemanager.service.XmindService;
import com.alsystem.casemanager.util.SecurityUtil;
import com.alsystem.casemanager.vo.AiChatResponseVO;
import com.alsystem.casemanager.vo.AiChatSessionCreatedVO;
import com.alsystem.casemanager.vo.AiChatSessionDetailVO;
import com.alsystem.casemanager.vo.AiChatSessionListVO;

import java.util.List;
import javax.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/case/ai")
public class CaseAiController {

  private final AiCaseService aiCaseService;
  private final AiChatSessionService aiChatSessionService;
  private final SecurityUtil securityUtil;
  private final XmindService xmindService;

  /**
   * 构造函数：注入 AI 用例服务与会话服务。
   */
  public CaseAiController(
      AiCaseService aiCaseService,
      AiChatSessionService aiChatSessionService,
      SecurityUtil securityUtil,XmindService xmindService) {
    this.aiCaseService = aiCaseService;
    this.aiChatSessionService = aiChatSessionService;
    this.securityUtil = securityUtil;
    this.xmindService = xmindService;
  }

  /**
   * 同步 AI 对话接口（workflow 模式）。
   */
  @PostMapping("/chat")
  @OperationAuditLog(operationType = "AI生成", apiPurpose = "工作流生成测试用例")
  public Result<AiChatResponseVO> chat(@Valid @RequestBody AiChatRequestDTO dto) {
    // 调用 AI 业务服务并返回统一响应结构。
    return Result.ok(aiCaseService.chat(dto));
  }

  /**
   * 流式 AI 对话接口（agent 模式，SSE）。
   */
  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @OperationAuditLog(operationType = "AI生成", apiPurpose = "Agent修改并生成测试用例")
  public SseEmitter chatStream(@Valid @RequestBody AiChatRequestDTO dto) {
    // 设置较长超时，保障长回复可完整推送。
    SseEmitter emitter = new SseEmitter(900_000L);
    // 异步线程中向 emitter 推送 delta/done/error 事件。
    aiCaseService.chatStream(dto, emitter);
    return emitter;
  }

  /** 当前项目下的会话列表（按更新时间倒序） */
  @GetMapping("/sessions")
  public Result<List<AiChatSessionListVO>> listSessions(@RequestParam String projectId) {
    // 读取当前登录用户作为会话所有者。
    long uid = securityUtil.currentUserId();
    // 按项目查询当前用户的会话列表。
    return Result.ok(aiChatSessionService.listSessions(uid, Long.parseLong(projectId.trim())));
  }

  /** 新建空会话（可选；不传则在首次对话成功后由服务端自动创建） */
  @PostMapping("/sessions")
  public Result<AiChatSessionCreatedVO> createSession(@Valid @RequestBody AiSessionCreateDTO dto) {
    // 创建空会话，供前端先占位再聊天。
    long uid = securityUtil.currentUserId();
    String sid = aiChatSessionService.createEmptySession(uid, Long.parseLong(dto.getProjectId().trim()));
    AiChatSessionCreatedVO vo = new AiChatSessionCreatedVO();
    vo.setSessionId(sid);
    return Result.ok(vo);
  }

  /** 会话详情：消息列表 + 工作流上下文，用于恢复界面 */
  @GetMapping("/sessions/{sessionId}")
  public Result<AiChatSessionDetailVO> detail(
      @PathVariable String sessionId, @RequestParam String projectId) {
    // 会话详情包含消息与上下文，用于前端恢复对话状态。
    long uid = securityUtil.currentUserId();
    return Result.ok(
        aiChatSessionService.getSessionDetail(uid, Long.parseLong(projectId.trim()), sessionId));
  }

  /**
   * 采用 AI 用例
   * @param content 用例内容---md格式
   * @return xmind文件下载
   * @throws Exception
   */
  @PostMapping("/download")
  public ResponseEntity<byte[]> downloadXmind(@RequestBody ConvertDto content) throws Exception {
    byte[] data = xmindService.convertMdToXmind(content.getMdContent());
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=map.xmind")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
  }

}
