package com.alsystem.casemanager.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
/** AI 对话消息 DTO。 */
public class AiChatMessageDTO {

  @NotBlank(message = "消息角色不能为空")
  private String role;

  /**
   * 允许为空（如流式占位）；历史多轮里助手内容可能较长，不能为空串时用占位避免校验失败
   */
  private String content;
}
