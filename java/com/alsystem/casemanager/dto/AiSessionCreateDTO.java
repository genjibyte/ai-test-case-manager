package com.alsystem.casemanager.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
/** 创建 AI 会话请求 DTO。 */
public class AiSessionCreateDTO {

  @NotBlank(message = "项目ID不能为空")
  private String projectId;
}
