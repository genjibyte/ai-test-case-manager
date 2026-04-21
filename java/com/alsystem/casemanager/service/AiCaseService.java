package com.alsystem.casemanager.service;

import com.alsystem.casemanager.dto.AiChatRequestDTO;
import com.alsystem.casemanager.vo.AiChatResponseVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiCaseService {

  /**
   * workflow 模式：同步返回完整结果（可聚合上游 SSE 或 JSON）。
   */
  AiChatResponseVO chat(AiChatRequestDTO dto);

  /**
   * agent 模式：将上游流式数据转发给前端 SSE。
   */
  void chatStream(AiChatRequestDTO dto, SseEmitter emitter);
}
