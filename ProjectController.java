package com.alsystem.casemanager.controller;

import com.alsystem.casemanager.annotation.OperationAuditLog;
import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.dto.CreateProjectDTO;
import com.alsystem.casemanager.dto.UpdateProjectDTO;
import com.alsystem.casemanager.service.ProjectService;
import com.alsystem.casemanager.vo.PageResult;
import com.alsystem.casemanager.vo.ProjectListItemVO;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project")
public class ProjectController {

  private final ProjectService projectService;

  /**
   * 构造函数：注入项目服务。
   *
   * @param projectService 项目服务
   */
  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  /**
   * 分页查询项目列表。
   */
  @GetMapping("/list")
  public Result<PageResult<ProjectListItemVO>> list(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String projectName,
      @RequestParam(required = false) Long maintainerId,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    // 委托服务层执行权限过滤、条件查询与分页。
    return Result.ok(
        projectService.list(pageNum, pageSize, projectName, maintainerId, status, startTime, endTime));
  }

  /**
   * 创建项目。
   */
  @PostMapping("/create")
  @OperationAuditLog(operationType = "新增", apiPurpose = "创建测试项目")
  public Result<String> create(@Valid @RequestBody CreateProjectDTO dto) {
    // 执行创建并返回主键 ID。
    Long id = projectService.create(dto);
    return Result.ok(String.valueOf(id));
  }

  /**
   * 更新项目信息。
   */
  @PutMapping("/update")
  @OperationAuditLog(operationType = "修改", apiPurpose = "修改项目基础信息")
  public Result<Void> update(@Valid @RequestBody UpdateProjectDTO dto) {
    // 由服务层进行权限校验、重名校验与更新。
    projectService.update(dto);
    return Result.ok(null);
  }

  /**
   * 删除项目。
   */
  @DeleteMapping("/delete/{projectId}")
  @OperationAuditLog(operationType = "删除", apiPurpose = "删除指定项目")
  public Result<Void> delete(@PathVariable String projectId) {
    // 将路径参数 projectId 转成 long 再删除。
    projectService.delete(Long.parseLong(projectId.trim()));
    return Result.ok(null);
  }

  /**
   * 查询项目详情。
   */
  @GetMapping("/{projectId}")
  public Result<ProjectListItemVO> detail(@PathVariable String projectId) {
    // 详情接口也会触发服务层权限校验。
    return Result.ok(projectService.detail(Long.parseLong(projectId.trim())));
  }
}
