package com.alsystem.casemanager.service;

import com.alsystem.casemanager.dto.CreateProjectDTO;
import com.alsystem.casemanager.dto.UpdateProjectDTO;
import com.alsystem.casemanager.vo.PageResult;
import com.alsystem.casemanager.vo.ProjectListItemVO;

public interface ProjectService {

  /**
   * 分页查询项目列表。
   */
  PageResult<ProjectListItemVO> list(
      int pageNum,
      int pageSize,
      String projectName,
      Long maintainerId,
      Integer status,
      String startTime,
      String endTime);

  /**
   * 创建项目。
   */
  Long create(CreateProjectDTO dto);

  /**
   * 更新项目。
   */
  void update(UpdateProjectDTO dto);

  /**
   * 删除项目。
   */
  void delete(Long projectId);

  /**
   * 查询项目详情。
   */
  ProjectListItemVO detail(Long projectId);
}
