package com.alsystem.casemanager.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
/** AI 会话列表项 VO。 */
public class AiChatSessionListVO {

  private String sessionId;
  private String title;
  private LocalDateTime updateTime;
}
