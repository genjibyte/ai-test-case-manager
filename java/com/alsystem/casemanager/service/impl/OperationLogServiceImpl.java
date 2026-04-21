package com.alsystem.casemanager.service.impl;

import com.alsystem.casemanager.dto.OperationLogQueryDTO;
import com.alsystem.casemanager.entity.SysOperationLog;
import com.alsystem.casemanager.mapper.SysOperationLogMapper;
import com.alsystem.casemanager.service.OperationLogService;
import com.alsystem.casemanager.vo.OperationLogItemVO;
import com.alsystem.casemanager.vo.PageResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationLogServiceImpl implements OperationLogService {
  private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final SysOperationLogMapper operationLogMapper;

  public OperationLogServiceImpl(SysOperationLogMapper operationLogMapper) {
    this.operationLogMapper = operationLogMapper;
  }

  @Override
  public PageResult<OperationLogItemVO> list(OperationLogQueryDTO query) {
    int pageNum = query.getPageNum() <= 0 ? 1 : query.getPageNum();
    int pageSize = query.getPageSize() <= 0 ? 20 : query.getPageSize();
    String operatorName = safeTrim(query.getOperatorName());
    String operationType = safeTrim(query.getOperationType());
    String apiPath = safeTrim(query.getApiPath());
    String status = safeTrim(query.getStatus());
    String startTime = safeTrim(query.getStartTime());
    String endTime = safeTrim(query.getEndTime());

    Page<SysOperationLog> page =
        operationLogMapper.selectPage(
            new Page<>(pageNum, pageSize),
            Wrappers.<SysOperationLog>lambdaQuery()
                .like(StringUtils.hasText(operatorName), SysOperationLog::getOperatorName, operatorName)
                .eq(StringUtils.hasText(operationType), SysOperationLog::getOperationType, operationType)
                .like(StringUtils.hasText(apiPath), SysOperationLog::getApiPath, apiPath)
                .eq(StringUtils.hasText(status), SysOperationLog::getStatus, status)
                .ge(
                    StringUtils.hasText(startTime),
                    SysOperationLog::getOperationTime,
                    parseDateTime(startTime, true))
                .le(
                    StringUtils.hasText(endTime),
                    SysOperationLog::getOperationTime,
                    parseDateTime(endTime, false))
                .orderByDesc(SysOperationLog::getOperationTime));

    List<OperationLogItemVO> list = new ArrayList<>();
    for (SysOperationLog item : page.getRecords()) {
      list.add(toItem(item));
    }
    return new PageResult<>(page.getTotal(), list, pageNum, pageSize, page.getPages());
  }

  private String safeTrim(String value) {
    return value == null ? null : value.trim();
  }

  private LocalDateTime parseDateTime(String source, boolean start) {
    String value = source == null ? "" : source.trim();
    if (!StringUtils.hasText(value)) {
      return null;
    }
    if (value.length() == 10) {
      return start
          ? LocalDateTime.parse(value + " 00:00:00", DT)
          : LocalDateTime.parse(value + " 23:59:59", DT);
    }
    return LocalDateTime.parse(value, DT);
  }

  private OperationLogItemVO toItem(SysOperationLog item) {
    OperationLogItemVO vo = new OperationLogItemVO();
    vo.setId(item.getId() == null ? null : String.valueOf(item.getId()));
    vo.setOperatorName(item.getOperatorName());
    vo.setOperationType(item.getOperationType());
    vo.setApiPath(item.getApiPath());
    vo.setHttpMethod(item.getHttpMethod());
    vo.setApiPurpose(item.getApiPurpose());
    vo.setProjectId(item.getProjectId() == null ? null : String.valueOf(item.getProjectId()));
    vo.setFileName(item.getFileName());
    vo.setCaseName(item.getCaseName());
    vo.setUploadPath(item.getUploadPath());
    vo.setStatus(item.getStatus());
    vo.setErrorMessage(item.getErrorMessage());
    vo.setOperationTime(item.getOperationTime() == null ? null : item.getOperationTime().format(DT));
    return vo;
  }
}

