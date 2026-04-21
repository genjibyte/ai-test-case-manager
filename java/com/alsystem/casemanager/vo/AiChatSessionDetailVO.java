package com.alsystem.casemanager.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
/** AI 会话详情 VO。 */
public class AiChatSessionDetailVO {

  private String sessionId;
  private String title;
  /** 解析后的消息列表 */
  private JsonNode messages;
  /** 工作流首轮上下文 JSON 字符串（与前端 workflowContextJson 一致） */
  private String workflowContextJson;
  private LocalDateTime updateTime;
}
