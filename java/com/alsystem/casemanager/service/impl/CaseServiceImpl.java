package com.alsystem.casemanager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alsystem.casemanager.dto.CreateCaseDTO;
import com.alsystem.casemanager.dto.UpdateCaseDTO;
import com.alsystem.casemanager.entity.SysUser;
import com.alsystem.casemanager.entity.TestCase;
import com.alsystem.casemanager.entity.TestFolder;
import com.alsystem.casemanager.mapper.TestCaseMapper;
import com.alsystem.casemanager.mapper.TestFolderMapper;
import com.alsystem.casemanager.service.CaseService;
import com.alsystem.casemanager.service.ProjectService;
import com.alsystem.casemanager.util.SecurityUtil;
import com.alsystem.casemanager.vo.CaseDetailVO;
import com.alsystem.casemanager.vo.CaseListItemVO;
import com.alsystem.casemanager.vo.PageResult;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseServiceImpl implements CaseService {

  private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final String DEFAULT_MIND =
      "{\"nodeData\":{\"topic\":\"新用例\",\"id\":\"root\",\"children\":[]}}";

  private final TestCaseMapper caseMapper;
  private final TestFolderMapper folderMapper;
  private final SecurityUtil securityUtil;
  private final ProjectService projectService;

  /**
   * 构造函数：注入用例、目录、安全与项目服务依赖。
   */
  public CaseServiceImpl(
      TestCaseMapper caseMapper,
      TestFolderMapper folderMapper,
      SecurityUtil securityUtil,
      ProjectService projectService) {
    this.caseMapper = caseMapper;
    this.folderMapper = folderMapper;
    this.securityUtil = securityUtil;
    this.projectService = projectService;
  }

  @Override
  /**
   * 分页查询用例列表。
   */
  public PageResult<CaseListItemVO> list(
      int pageNum,
      int pageSize,
      Long projectId,
      String caseName,
      Integer caseLevel,
      Integer caseStatus,
      String folderPath) {
    // 先校验项目可访问性；不存在或无权限时直接抛错。
    projectService.detail(projectId);
    // 构建动态查询条件。
    LambdaQueryWrapper<TestCase> w = Wrappers.lambdaQuery();
    w.eq(TestCase::getProjectId, projectId);
    if (caseName != null && !caseName.isEmpty()) {
      w.like(TestCase::getCaseName, caseName);
    }
    if (caseLevel != null) {
      w.eq(TestCase::getCaseLevel, caseLevel);
    }
    if (caseStatus != null) {
      w.eq(TestCase::getCaseStatus, caseStatus);
    }
    if (folderPath != null && !folderPath.isEmpty()) {
      w.eq(TestCase::getFolderPath, folderPath);
    }
    // 按最近更新时间倒序，保证前端先看到最新改动。
    w.orderByDesc(TestCase::getUpdateTime);
    // 执行分页查询。
    Page<TestCase> page = caseMapper.selectPage(new Page<>(pageNum, pageSize), w);
    // 将实体列表映射为列表展示 VO。
    List<CaseListItemVO> list = page.getRecords().stream().map(this::toListItem).collect(Collectors.toList());
    return new PageResult<>(page.getTotal(), list, page.getCurrent(), page.getSize(), page.getPages());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  /**
   * 创建用例并写入数据库。
   */
  public Long create(CreateCaseDTO dto){
    // 读取当前登录用户作为创建人。
    SysUser me = securityUtil.currentUser();
    // 字符串参数转类型。
    long projectId = Long.parseLong(dto.getProjectId().trim());
    Long folderId = null;
    if (dto.getFolderId() != null && !dto.getFolderId().trim().isEmpty()) {
      folderId = Long.parseLong(dto.getFolderId().trim());
    }
    // 校验项目存在且当前用户可访问。
    projectService.detail(projectId);
    // 校验项目内 caseCode 唯一。
    long dup =
        caseMapper.selectCount(
            Wrappers.<TestCase>lambdaQuery()
                .eq(TestCase::getProjectId, projectId)
                .eq(TestCase::getCaseCode, dto.getCaseCode()));
    if (dup > 0) {
      throw new IllegalArgumentException("项目内用例编号已存在");
    }
    // 优先用前端指定路径，默认根目录。
    String path = dto.getFolderPath() != null ? dto.getFolderPath() : "/";
    if (folderId != null) {
      // 若传了 folderId，校验文件夹归属并按真实树路径回填。
      TestFolder folder = folderMapper.selectById(folderId);
      if (folder == null || !folder.getProjectId().equals(projectId)) {
        throw new IllegalArgumentException("文件夹不存在");
      }
      path = buildFolderFullPath(folder);
    }
    // 组装实体并设置默认字段。
    TestCase c = new TestCase();
    c.setProjectId(projectId);
    c.setCaseCode(dto.getCaseCode());
    c.setCaseName(dto.getCaseName());
    c.setCaseType(1);
    c.setCaseLevel(dto.getCaseLevel() != null ? dto.getCaseLevel() : 3);
    c.setCaseStatus(1);
    c.setFolderPath(path);
    c.setFolderId(folderId);
    // 存储 mdContent；若为空则回退默认脑图 JSON。
    c.setMindMapJson(dto.getMdContent() != null ? dto.getMdContent() : DEFAULT_MIND);
    c.setCreateBy(me.getUserId());
    // 持久化并返回主键。
    caseMapper.insert(c);
    return c.getCaseId();
  }

  /**
   * 递归构建文件夹完整路径。
   */
  private String buildFolderFullPath(TestFolder f) {
    if (f.getParentId() == null) {
      return "/" + f.getFolderName();
    }
    TestFolder p = folderMapper.selectById(f.getParentId());
    if (p == null) {
      return "/" + f.getFolderName();
    }
    return buildFolderFullPath(p) + "/" + f.getFolderName();
  }

  @Override
  /**
   * 查询用例详情。
   */
  public CaseDetailVO detail(Long caseId) {
    // 先查用例实体。
    TestCase c = caseMapper.selectById(caseId);
    if (c == null) {
      throw new IllegalArgumentException("用例不存在");
    }
    // 触发项目访问校验。
    projectService.detail(c.getProjectId());
    // 转换为详情 VO。
    return toDetail(c);
  }

  @Override
  /**
   * 更新用例。
   */
  public void update(UpdateCaseDTO dto) {
    // 读取当前用户作为更新人。
    SysUser me = securityUtil.currentUser();
    long caseId = Long.parseLong(dto.getCaseId().trim());
    TestCase c = caseMapper.selectById(caseId);
    if (c == null) {
      throw new IllegalArgumentException("用例不存在");
    }
    // 校验项目可访问后再更新。
    projectService.detail(c.getProjectId());
    // 仅更新有值字段，避免覆盖未传值。
    if (dto.getCaseName() != null) {
      c.setCaseName(dto.getCaseName());
    }
    if (dto.getMindMapJson() != null) {
      c.setMindMapJson(dto.getMindMapJson());
    }
    c.setUpdateBy(me.getUserId());
    // 提交更新。
    caseMapper.updateById(c);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  /**
   * 删除用例。
   */
  public void delete(Long caseId) {
    if (caseId == null) {
      throw new IllegalArgumentException("用例ID不能为空");
    }
    // 查询用例并校验存在。
    TestCase c = caseMapper.selectById(caseId);
    if (c == null) {
      throw new IllegalArgumentException("用例不存在");
    }
    // 校验项目访问权限后删除。
    projectService.detail(c.getProjectId());
    caseMapper.deleteById(caseId);
  }

  /**
   * 实体转列表项 VO。
   */
  private CaseListItemVO toListItem(TestCase c) {
    CaseListItemVO vo = new CaseListItemVO();
    vo.setCaseId(String.valueOf(c.getCaseId()));
    vo.setProjectId(String.valueOf(c.getProjectId()));
    vo.setCaseName(c.getCaseName());
    vo.setCaseCode(c.getCaseCode());
    vo.setCaseLevel(c.getCaseLevel());
    vo.setCaseStatus(c.getCaseStatus());
    if (c.getCreateTime() != null) {
      vo.setCreateTime(c.getCreateTime().format(DT));
    }
    vo.setFolderPath(c.getFolderPath());
    return vo;
  }

  /**
   * 实体转详情 VO。
   */
  private CaseDetailVO toDetail(TestCase c) {
    CaseDetailVO vo = new CaseDetailVO();
    vo.setCaseId(String.valueOf(c.getCaseId()));
    vo.setProjectId(String.valueOf(c.getProjectId()));
    vo.setCaseName(c.getCaseName());
    vo.setCaseCode(c.getCaseCode());
    vo.setCaseLevel(c.getCaseLevel());
    vo.setCaseStatus(c.getCaseStatus());
    vo.setFolderPath(c.getFolderPath());
    vo.setMindMapJson(c.getMindMapJson());
    if (c.getCreateTime() != null) {
      vo.setCreateTime(c.getCreateTime().format(DT));
    }
    if (c.getUpdateTime() != null) {
      vo.setUpdateTime(c.getUpdateTime().format(DT));
    }
    return vo;
  }
}
