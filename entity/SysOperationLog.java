package com.alsystem.casemanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long operatorId;
  private String operatorName;
  private LocalDateTime operationTime;
  private String operationType;
  private String apiPath;
  private String httpMethod;
  private String apiPurpose;
  private Long projectId;
  private String fileName;
  private String caseName;
  private String uploadPath;
  private String status;
  private String errorMessage;
  private LocalDateTime createTime;
}

