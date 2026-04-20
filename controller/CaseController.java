package com.alsystem.casemanager.controller;

import com.alsystem.casemanager.annotation.OperationAuditLog;
import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.dto.CreateCaseDTO;
import com.alsystem.casemanager.dto.UpdateCaseDTO;
import com.alsystem.casemanager.service.CaseService;
import com.alsystem.casemanager.vo.CaseDetailVO;
import com.alsystem.casemanager.vo.CaseListItemVO;
import com.alsystem.casemanager.vo.PageResult;
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
@RequestMapping("/api/case")
public class CaseController {

  private final CaseService caseService;

  /**
   * 构造函数：注入用例服务。
   *
   * @param caseService 用例领域服务
   */
  public CaseController(CaseService caseService) {
    this.caseService = caseService;
  }

  /**
   * 分页查询用例列表。
   *
   * @param pageNum 页码（从 1 开始）
   * @param pageSize 每页条数
   * @param projectId 项目 ID（字符串形式，后续转 long）
   * @param caseName 用例名称（模糊查询，可空）
   * @param caseLevel 用例等级（可空）
   * @param caseStatus 用例状态（可空）
   * @param folderPath 目录路径（可空）
   * @return 统一响应包装后的分页结果
   */
  @GetMapping("/list")
  public Result<PageResult<CaseListItemVO>> list(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam String projectId,
      @RequestParam(required = false) String caseName,
      @RequestParam(required = false) Integer caseLevel,
      @RequestParam(required = false) Integer caseStatus,
      @RequestParam(required = false) String folderPath) {
    // 参数规范化：将前端字符串 projectId 转为 long。
    long pid = Long.parseLong(projectId.trim());
    // 调用服务层执行过滤查询与分页聚合。
    return Result.ok(
        caseService.list(
            pageNum, pageSize, pid, caseName, caseLevel, caseStatus, folderPath));
  }

  /**
   * 新建用例。
   *
   * @param dto 创建请求体
   * @return 新建后的用例 ID（字符串）
   */
  @PostMapping("/create")
  public Result<String> create(@Valid @RequestBody CreateCaseDTO dto) {
    // 调用服务层执行创建与业务校验。
    Long id = caseService.create(dto);
    return Result.ok(String.valueOf(id));
  }

  /**
   * 查询用例详情。
   *
   * @param caseId 用例 ID（字符串）
   * @return 用例详情
   */
  @GetMapping("/{caseId}")
  public Result<CaseDetailVO> detail(@PathVariable String caseId) {
    // 将路径参数转为 long 后查询详情。
    return Result.ok(caseService.detail(Long.parseLong(caseId.trim())));
  }

  /**
   * 更新用例基础信息。
   *
   * @param dto 更新请求体
   * @return 空结果
   */
  @PutMapping("/update")
  @OperationAuditLog(operationType = "修改", apiPurpose = "编辑项目文件内容")
  public Result<Void> update(@Valid @RequestBody UpdateCaseDTO dto) {
    // 调用服务层执行业务校验与更新写库。
    caseService.update(dto);
    return Result.ok(null);
  }

  /**
   * 删除用例。
   *
   * @param caseId 用例 ID（字符串）
   * @return 空结果
   */
  @DeleteMapping("/{caseId}")
  public Result<Void> delete(@PathVariable String caseId) {
    // 转换 ID 并委托服务层删除。
    caseService.delete(Long.parseLong(caseId.trim()));
    return Result.ok(null);
  }
}
