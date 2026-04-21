package com.alsystem.casemanager.vo;

import lombok.Data;

@Data
/** 用例详情 VO。 */
public class CaseDetailVO {

  private String caseId;
  private String projectId;
  private String caseName;
  private String caseCode;
  private Integer caseLevel;
  private Integer caseStatus;
  private String folderPath;
  private String mindMapJson;
  private String createTime;
  private String updateTime;
}
