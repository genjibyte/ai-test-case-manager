package com.alsystem.casemanager.dto;

import lombok.Data;

@Data
public class OperationLogQueryDTO {
  private int pageNum = 1;
  private int pageSize = 20;
  private String operatorName;
  private String operationType;
  private String apiPath;
  private String status;
  private String startTime;
  private String endTime;
}

