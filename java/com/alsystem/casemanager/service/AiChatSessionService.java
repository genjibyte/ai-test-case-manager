package com.alsystem.casemanager.service;

import com.alsystem.casemanager.dto.AiChatRequestDTO;
import com.alsystem.casemanager.vo.AiChatResponseVO;
import com.alsystem.casemanager.vo.AiChatSessionDetailVO;
import com.alsystem.casemanager.vo.AiChatSessionListVO;
import java.util.List;

public interface AiChatSessionService {

  /**
   * 创建空会话。
   */
  String createEmptySession(Long userId, Long projectId);

  /**
   * 查询某用户在某项目下的会话列表。
   */
  List<AiChatSessionListVO> listSessions(Long userId, Long projectId);

  /**
   * 查询会话详情（消息与工作流上下文）。
   */
  AiChatSessionDetailVO getSessionDetail(Long userId, Long projectId, String sessionKey);

  /**
   * 工作流对话完成后持久化会话，并返回会话 ID。
   */
  String persistAfterWorkflow(AiChatRequestDTO dto, AiChatResponseVO vo, Long userId);

  /**
   * Agent 流式结束后持久化会话。
   */
  String persistAfterAgent(
      AiChatRequestDTO dto, String assistantReply, String cozeConversationId, Long userId);

  /**
   * 读取与会话关联的上游 conversation_id。
   */
  String getCozeConversationId(Long userId, Long projectId, String sessionKey);
}
