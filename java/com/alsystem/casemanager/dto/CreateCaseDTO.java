package com.alsystem.casemanager.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
/** 创建用例请求 DTO。 */
public class CreateCaseDTO {

  /** 字符串形式，避免前端 Number 造成雪花 ID 精度丢失 */
  @NotBlank(message = "项目ID不能为空")
  private String projectId;

  @NotBlank(message = "用例名称不能为空")
  private String caseName;

  @NotBlank(message = "用例编号不能为空")
  private String caseCode;

  private String folderPath = "/";

  /** 所在文件夹 ID，可选 */
  private String folderId;

  /** 用例内容 */
  private String mdContent;

  /** 用例等级 1-3，可选 */
  private Integer caseLevel;

}
