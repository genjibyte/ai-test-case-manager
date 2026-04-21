package com.alsystem.casemanager.service;

import com.alsystem.casemanager.dto.CreateCaseDTO;
import com.alsystem.casemanager.dto.UpdateCaseDTO;
import com.alsystem.casemanager.vo.CaseDetailVO;
import com.alsystem.casemanager.vo.CaseListItemVO;
import com.alsystem.casemanager.vo.PageResult;

public interface CaseService {

  /**
   * 分页查询用例列表。
   */
  PageResult<CaseListItemVO> list(
      int pageNum,
      int pageSize,
      Long projectId,
      String caseName,
      Integer caseLevel,
      Integer caseStatus,
      String folderPath);

  /**
   * 创建用例。
   */
  Long create(CreateCaseDTO dto);

  /**
   * 查询用例详情。
   */
  CaseDetailVO detail(Long caseId);

  /**
   * 更新用例。
   */
  void update(UpdateCaseDTO dto);

  /**
   * 删除用例。
   */
  void delete(Long caseId);
}
