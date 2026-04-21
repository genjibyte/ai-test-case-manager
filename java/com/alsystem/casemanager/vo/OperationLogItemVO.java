package com.alsystem.casemanager.vo;

import lombok.Data;

@Data
public class OperationLogItemVO {
  private String id;
  private String operatorName;
  private String operationTime;
  private String operationType;
  private String apiPath;
  private String httpMethod;
  private String apiPurpose;
  private String projectId;
  private String fileName;
  private String caseName;
  private String uploadPath;
  private String status;
  private String errorMessage;
}

