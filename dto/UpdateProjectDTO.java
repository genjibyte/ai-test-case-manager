package com.alsystem.casemanager.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
/** 更新项目请求 DTO。 */
public class UpdateProjectDTO extends CreateProjectDTO {

  /** 字符串形式，避免前端 Number 造成雪花 ID 精度丢失 */
  @NotBlank(message = "项目ID不能为空")
  private String projectId;
}
