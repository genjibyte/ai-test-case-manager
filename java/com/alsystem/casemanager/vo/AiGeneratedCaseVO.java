package com.alsystem.casemanager.vo;

import lombok.Data;

/** AI 生成结果预览（未落库），前端可据此调用创建用例接口保存。 */
@Data
public class AiGeneratedCaseVO {

  private String tempId;
  private String projectId;
  private String caseName;
  private String caseCode;
  private Integer caseLevel;
  private Integer caseStatus;
  /** Mind Elixir 脑图 JSON，保存用例时原样写入 */
  private String mindMapJson;
}
