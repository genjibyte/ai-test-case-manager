package com.alsystem.casemanager.vo;

import lombok.Data;

@Data
/** 用例列表项 VO。 */
public class CaseListItemVO {

  private String caseId;
  private String projectId;
  private String caseName;
  private String caseCode;
  private Integer caseLevel;
  private Integer caseStatus;
  private String createTime;
  private String folderPath;
}
