package com.alsystem.casemanager.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alsystem.casemanager.dto.CreateFolderDTO;
import com.alsystem.casemanager.dto.RenameFolderDTO;
import com.alsystem.casemanager.entity.SysUser;
import com.alsystem.casemanager.entity.TestCase;
import com.alsystem.casemanager.entity.TestFolder;
import com.alsystem.casemanager.mapper.TestCaseMapper;
import com.alsystem.casemanager.mapper.TestFolderMapper;
import com.alsystem.casemanager.service.FolderService;
import com.alsystem.casemanager.service.ProjectService;
import com.alsystem.casemanager.util.SecurityUtil;
import com.alsystem.casemanager.vo.TreeNodeVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderServiceImpl implements FolderService {

  private final TestFolderMapper folderMapper;
  private final TestCaseMapper caseMapper;
  private final SecurityUtil securityUtil;
  private final ProjectService projectService;

  /**
   * 构造函数：注入目录/用例 Mapper 与安全相关服务。
   */
  public FolderServiceImpl(
      TestFolderMapper folderMapper,
      TestCaseMapper caseMapper,
      SecurityUtil securityUtil,
      ProjectService projectService) {
    this.folderMapper = folderMapper;
    this.caseMapper = caseMapper;
    this.securityUtil = securityUtil;
    this.projectService = projectService;
  }

  @Override
  /**
   * 获取目录树（目录节点 + 用例节点）。
   */
  public List<TreeNodeVO> tree(Long projectId) {
    // 先校验项目访问权限。
    projectService.detail(projectId);
    // 一次性加载项目下目录与用例，后续内存组树。
    List<TestFolder> folders =
        folderMapper.selectList(
            Wrappers.<TestFolder>lambdaQuery().eq(TestFolder::getProjectId, projectId));
    List<TestCase> cases =
        caseMapper.selectList(
            Wrappers.<TestCase>lambdaQuery().eq(TestCase::getProjectId, projectId));
    // 从根节点开始递归构建树。
    return build(null, folders, cases);
  }

  /**
   * 递归构建树节点。
   */
  private List<TreeNodeVO> build(Long parentFolderId, List<TestFolder> folders, List<TestCase> cases) {
    List<TreeNodeVO> nodes = new ArrayList<>();
    for (TestFolder f : folders) {
      boolean atRoot = parentFolderId == null && f.getParentId() == null;
      boolean under =
          parentFolderId != null && f.getParentId() != null && f.getParentId().equals(parentFolderId);
      // 当前层命中的目录节点。
      if (atRoot || under) {
        TreeNodeVO n = new TreeNodeVO();
        n.setId("f-" + f.getFolderId());
        n.setLabel(f.getFolderName());
        n.setType("folder");
        n.setParentId(f.getParentId() == null ? null : "f-" + f.getParentId());
        n.setPath(buildFolderPath(f, folders));
        // 递归构建子节点。
        n.setChildren(build(f.getFolderId(), folders, cases));
        nodes.add(n);
      }
    }
    for (TestCase c : cases) {
      boolean atRoot = parentFolderId == null && c.getFolderId() == null;
      boolean under =
          parentFolderId != null && c.getFolderId() != null && c.getFolderId().equals(parentFolderId);
      // 当前层命中的用例文件节点。
      if (atRoot || under) {
        nodes.add(caseNode(c));
      }
    }
    // 同层按标签排序，保证前端展示稳定。
    nodes.sort(Comparator.comparing(TreeNodeVO::getLabel));
    return nodes;
  }

  /**
   * 递归构建目录完整路径。
   */
  private String buildFolderPath(TestFolder f, List<TestFolder> all) {
    if (f.getParentId() == null) {
      return "/" + f.getFolderName();
    }
    TestFolder p = all.stream().filter(x -> x.getFolderId().equals(f.getParentId())).findFirst().orElse(null);
    if (p == null) {
      return "/" + f.getFolderName();
    }
    return buildFolderPath(p, all) + "/" + f.getFolderName();
  }

  /**
   * 用例实体转换为文件节点。
   */
  private TreeNodeVO caseNode(TestCase c) {
    TreeNodeVO n = new TreeNodeVO();
    n.setId("c-" + c.getCaseId());
    n.setLabel(c.getCaseName());
    n.setType("file");
    n.setParentId(c.getFolderId() == null ? null : "f-" + c.getFolderId());
    n.setPath(c.getFolderPath() != null ? c.getFolderPath() : "/");
    n.setCaseId(String.valueOf(c.getCaseId()));
    n.setChildren(new ArrayList<>());
    return n;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  /**
   * 创建文件夹。
   */
  public Long create(CreateFolderDTO dto) {
    SysUser me = securityUtil.currentUser();
    long projectId = Long.parseLong(dto.getProjectId().trim());
    Long parentId = null;
    if (dto.getParentId() != null && !dto.getParentId().trim().isEmpty()) {
      parentId = Long.parseLong(dto.getParentId().trim());
    }
    // 校验目标项目可访问。
    projectService.detail(projectId);
    if (parentId != null) {
      // 校验父目录存在且属于同项目。
      TestFolder parent = folderMapper.selectById(parentId);
      if (parent == null || !parent.getProjectId().equals(projectId)) {
        throw new IllegalArgumentException("父文件夹不存在");
      }
    }
    // 校验同级目录不重名。
    long dup =
        folderMapper.selectCount(
            Wrappers.<TestFolder>lambdaQuery()
                .eq(TestFolder::getProjectId, projectId)
                .eq(TestFolder::getParentId, parentId)
                .eq(TestFolder::getFolderName, dto.getFolderName()));
    if (dup > 0) {
      throw new IllegalArgumentException("同级目录下名称已存在");
    }
    // 写库并返回主键。
    TestFolder f = new TestFolder();
    f.setProjectId(projectId);
    f.setParentId(parentId);
    f.setFolderName(dto.getFolderName());
    f.setCreateBy(me.getUserId());
    folderMapper.insert(f);
    return f.getFolderId();
  }

  @Override
  /**
   * 重命名文件夹。
   */
  public void rename(Long folderId, RenameFolderDTO dto) {
    // 读取当前用户，保证在已登录上下文执行。
    SysUser me = securityUtil.currentUser();
    TestFolder f = folderMapper.selectById(folderId);
    if (f == null) {
      throw new IllegalArgumentException("文件夹不存在");
    }
    // 项目访问校验。
    projectService.detail(f.getProjectId());
    // 同级重名校验。
    long dup =
        folderMapper.selectCount(
            Wrappers.<TestFolder>lambdaQuery()
                .eq(TestFolder::getProjectId, f.getProjectId())
                .eq(TestFolder::getParentId, f.getParentId())
                .eq(TestFolder::getFolderName, dto.getFolderName())
                .ne(TestFolder::getFolderId, folderId));
    if (dup > 0) {
      throw new IllegalArgumentException("同级目录下名称已存在");
    }
    // 更新名称。
    f.setFolderName(dto.getFolderName());
    folderMapper.updateById(f);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  /**
   * 删除文件夹（要求无子目录且无用例）。
   */
  public void delete(Long folderId) {
    TestFolder f = folderMapper.selectById(folderId);
    if (f == null) {
      throw new IllegalArgumentException("文件夹不存在");
    }
    // 访问校验。
    projectService.detail(f.getProjectId());
    // 存在子目录则禁止删除。
    long sub =
        folderMapper.selectCount(
            Wrappers.<TestFolder>lambdaQuery().eq(TestFolder::getParentId, folderId));
    if (sub > 0) {
      throw new IllegalArgumentException("请先删除子文件夹");
    }
    // 目录下仍有用例则禁止删除。
    long cs =
        caseMapper.selectCount(
            Wrappers.<TestCase>lambdaQuery().eq(TestCase::getFolderId, folderId));
    if (cs > 0) {
      throw new IllegalArgumentException("请先移除文件夹下的用例");
    }
    // 条件满足后执行删除。
    folderMapper.deleteById(folderId);
  }
}
