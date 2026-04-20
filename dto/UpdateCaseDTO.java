package com.alsystem.casemanager.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
/** 更新用例请求 DTO。 */
public class UpdateCaseDTO {

  /** 字符串形式，避免前端 Number 造成雪花 ID 精度丢失 */
  @NotBlank(message = "用例ID不能为空")
  private String caseId;

  private String caseName;
  private String mindMapJson;
}
