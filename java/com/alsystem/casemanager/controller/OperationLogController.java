package com.alsystem.casemanager.controller;

import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.dto.OperationLogQueryDTO;
import com.alsystem.casemanager.service.OperationLogService;
import com.alsystem.casemanager.vo.OperationLogItemVO;
import com.alsystem.casemanager.vo.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/log")
public class OperationLogController {
  private final OperationLogService operationLogService;

  public OperationLogController(OperationLogService operationLogService) {
    this.operationLogService = operationLogService;
  }

  @GetMapping("/list")
  public Result<PageResult<OperationLogItemVO>> list(OperationLogQueryDTO query) {
    return Result.ok(operationLogService.list(query));
  }
}

