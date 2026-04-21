package com.alsystem.casemanager.service;

import com.alsystem.casemanager.dto.OperationLogQueryDTO;
import com.alsystem.casemanager.vo.OperationLogItemVO;
import com.alsystem.casemanager.vo.PageResult;

public interface OperationLogService {
  PageResult<OperationLogItemVO> list(OperationLogQueryDTO query);
}

