package com.alsystem.casemanager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alsystem.casemanager.dto.CreateProjectDTO;
import com.alsystem.casemanager.dto.UpdateProjectDTO;
import com.alsystem.casemanager.entity.SysUser;
import com.alsystem.casemanager.entity.TestProject;
import com.alsystem.casemanager.entity.TestProjectUser;
import com.alsystem.casemanager.mapper.SysUserMapper;
import com.alsystem.casemanager.mapper.TestProjectMapper;
import com.alsystem.casemanager.mapper.TestProjectUserMapper;
import com.alsystem.casemanager.service.ProjectService;
import com.alsystem.casemanager.util.SecurityUtil;
import com.alsystem.casemanager.vo.MaintainerVO;
import com.alsystem.casemanager.vo.PageResult;
import com.alsystem.casemanager.vo.ProjectListItemVO;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectServiceImpl implements ProjectService {

  private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final TestProjectMapper projectMapper;
  private final TestProjectUserMapper projectUserMapper;
  private final SysUserMapper userMapper;
  private final SecurityUtil securityUtil;

  /**
   * 构造函数：注入项目相关 Mapper 与安全工具。
   */
  public ProjectServiceImpl(
      TestProjectMapper projectMapper,
      TestProjectUserMapper projectUserMapper,
      SysUserMapper userMapper,
      SecurityUtil securityUtil) {
    this.projectMapper = projectMapper;
    this.projectUserMapper = projectUserMapper;
    this.userMapper = userMapper;
    this.securityUtil = securityUtil;
  }

  @Override
  /**
   * 分页查询项目列表（含权限过滤）。
   */
  public PageResult<ProjectListItemVO> list(
      int pageNum,
      int pageSize,
      String projectName,
      Long maintainerId,
      Integer status,
      String startTime,
      String endTime) {
    // 获取当前用户用于权限过滤。
    SysUser me = securityUtil.currentUser();
    LambdaQueryWrapper<TestProject> w = Wrappers.lambdaQuery();
    if (!securityUtil.isAdmin(me)) {
      // 非管理员只看自己关联项目。
      List<TestProjectUser> links =
          projectUserMapper.selectList(
              Wrappers.<TestProjectUser>lambdaQuery().eq(TestProjectUser::getUserId, me.getUserId()));
      List<Long> ids = links.stream().map(TestProjectUser::getProjectId).collect(Collectors.toList());
      if (ids.isEmpty()) {
        return new PageResult<>(0, new ArrayList<>(), pageNum, pageSize, 0);
      }
      w.in(TestProject::getProjectId, ids);
    }
    if (projectName != null && !projectName.isEmpty()) {
      // 名称/编码模糊检索。
      w.and(
          q ->
              q.like(TestProject::getProjectName, projectName)
                  .or()
                  .like(TestProject::getProjectCode, projectName));
    }
    if (status != null) {
      w.eq(TestProject::getStatus, status);
    }
    if (startTime != null && !startTime.isEmpty()) {
      // 起始时间按日期 00:00:00 生效。
      LocalDate s = LocalDate.parse(startTime, D);
      w.ge(TestProject::getCreateTime, s.atStartOfDay());
    }
    if (endTime != null && !endTime.isEmpty()) {
      // 结束时间按次日 00:00:00 之前（含当日整天）处理。
      LocalDate e = LocalDate.parse(endTime, D);
      w.le(TestProject::getCreateTime, e.plusDays(1).atStartOfDay());
    }
    if (maintainerId != null) {
      // 按维护人过滤项目。
      List<TestProjectUser> mus =
          projectUserMapper.selectList(
              Wrappers.<TestProjectUser>lambdaQuery().eq(TestProjectUser::getUserId, maintainerId));
      List<Long> pids = mus.stream().map(TestProjectUser::getProjectId).collect(Collectors.toList());
      if (pids.isEmpty()) {
        return new PageResult<>(0, new ArrayList<>(), pageNum, pageSize, 0);
      }
      w.in(TestProject::getProjectId, pids);
    }
    w.orderByDesc(TestProject::getCreateTime);
    // 执行分页查询并映射输出。
    Page<TestProject> page = projectMapper.selectPage(new Page<>(pageNum, pageSize), w);
    List<ProjectListItemVO> list = new ArrayList<>();
    for (TestProject p : page.getRecords()) {
      list.add(toItem(p));
    }
    long pages = page.getPages();
    return new PageResult<>(page.getTotal(), list, page.getCurrent(), page.getSize(), pages);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  /**
   * 创建项目并建立项目-成员关系。
   */
  public Long create(CreateProjectDTO dto) {
    // 当前用户作为创建者，同时加入维护人集合。
    SysUser me = securityUtil.currentUser();
    long nameCnt =
        projectMapper.selectCount(
            Wrappers.<TestProject>lambdaQuery().eq(TestProject::getProjectName, dto.getProjectName()));
    if (nameCnt > 0) {
      throw new IllegalArgumentException("项目名称已存在");
    }
    // 写入项目主表。
    TestProject p = new TestProject();
    p.setProjectName(dto.getProjectName());
    p.setProjectCode(UUID.randomUUID().toString().replace("-", ""));
    p.setProjectDesc(dto.getProjectDesc());
    p.setStatus(1);
    p.setCreateBy(me.getUserId());
    projectMapper.insert(p);
    // 解析维护人列表，并补充当前用户。
    Set<Long> mids = parseMaintainerIds(dto.getMaintainerIdList());
    mids.add(me.getUserId());
    for (Long uid : mids) {
      // 写入项目成员关系；管理员本人标记角色 1，其余为 2。
      TestProjectUser pu = new TestProjectUser();
      pu.setProjectId(p.getProjectId());
      pu.setUserId(uid);
      pu.setProjectRole(securityUtil.isAdmin(me) && uid.equals(me.getUserId()) ? 1 : 2);
      projectUserMapper.insert(pu);
    }
    return p.getProjectId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  /**
   * 更新项目并重建成员关系。
   */
  public void update(UpdateProjectDTO dto) {
    SysUser me = securityUtil.currentUser();
    long projectId = Long.parseLong(dto.getProjectId().trim());
    TestProject old = projectMapper.selectById(projectId);
    if (old == null) {
      throw new IllegalArgumentException("项目不存在");
    }
    // 校验项目访问权限。
    assertProjectAccess(me, projectId);
    long nameDup =
        projectMapper.selectCount(
            Wrappers.<TestProject>lambdaQuery()
                .eq(TestProject::getProjectName, dto.getProjectName())
                .ne(TestProject::getProjectId, projectId));
    if (nameDup > 0) {
      throw new IllegalArgumentException("项目名称已存在");
    }
    // 更新项目主信息。
    old.setProjectName(dto.getProjectName());
    old.setProjectDesc(dto.getProjectDesc());
    old.setUpdateBy(me.getUserId());
    projectMapper.updateById(old);
    // 删除旧成员关系后重建，保证和前端提交保持一致。
    projectUserMapper.delete(
        Wrappers.<TestProjectUser>lambdaQuery().eq(TestProjectUser::getProjectId, projectId));
    Set<Long> mids = parseMaintainerIds(dto.getMaintainerIdList());
    mids.add(me.getUserId());
    for (Long uid : mids) {
      TestProjectUser pu = new TestProjectUser();
      pu.setProjectId(projectId);
      pu.setUserId(uid);
      pu.setProjectRole(2);
      projectUserMapper.insert(pu);
    }
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  /**
   * 删除项目。
   */
  public void delete(Long projectId) {
    SysUser me = securityUtil.currentUser();
    assertProjectAccess(me, projectId);
    // 先删关系再删主表，避免孤儿关系数据。
    projectUserMapper.delete(
        Wrappers.<TestProjectUser>lambdaQuery().eq(TestProjectUser::getProjectId, projectId));
    projectMapper.deleteById(projectId);
  }

  @Override
  /**
   * 查询项目详情。
   */
  public ProjectListItemVO detail(Long projectId) {
    SysUser me = securityUtil.currentUser();
    assertProjectAccess(me, projectId);
    TestProject p = projectMapper.selectById(projectId);
    if (p == null) {
      throw new IllegalArgumentException("项目不存在");
    }
    // 项目存在后映射详情 VO。
    return toItem(p);
  }

  /**
   * 解析维护人 ID 字符串列表为 long 集合。
   */
  private static Set<Long> parseMaintainerIds(List<String> raw) {
    Set<Long> mids = new HashSet<>();
    if (raw == null) {
      return mids;
    }
    for (String s : raw) {
      if (s != null && !s.trim().isEmpty()) {
        mids.add(Long.parseLong(s.trim()));
      }
    }
    return mids;
  }

  /**
   * 校验当前用户是否可访问指定项目。
   */
  private void assertProjectAccess(SysUser me, Long projectId) {
    if (securityUtil.isAdmin(me)) {
      return;
    }
    long c =
        projectUserMapper.selectCount(
            Wrappers.<TestProjectUser>lambdaQuery()
                .eq(TestProjectUser::getProjectId, projectId)
                .eq(TestProjectUser::getUserId, me.getUserId()));
    if (c == 0) {
      throw new IllegalArgumentException("无权限访问该项目");
    }
  }

  /**
   * 项目实体转换为列表项 VO（含维护人信息）。
   */
  private ProjectListItemVO toItem(TestProject p) {
    ProjectListItemVO vo = new ProjectListItemVO();
    vo.setProjectId(String.valueOf(p.getProjectId()));
    vo.setProjectName(p.getProjectName());
    vo.setProjectCode(p.getProjectCode());
    vo.setProjectDesc(p.getProjectDesc());
    vo.setStatus(p.getStatus());
    if (p.getCreateTime() != null) {
      vo.setCreateTime(p.getCreateTime().format(DT));
    }
    // 查询项目成员关系并映射为维护人列表。
    List<TestProjectUser> links =
        projectUserMapper.selectList(
            Wrappers.<TestProjectUser>lambdaQuery().eq(TestProjectUser::getProjectId, p.getProjectId()));
    List<Long> uids = links.stream().map(TestProjectUser::getUserId).collect(Collectors.toList());
    List<MaintainerVO> maintainers = new ArrayList<>();
    if (!uids.isEmpty()) {
      List<SysUser> users =
          userMapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getUserId, uids));
      Map<Long, SysUser> map = users.stream().collect(Collectors.toMap(SysUser::getUserId, u -> u));
      for (Long uid : uids) {
        SysUser u = map.get(uid);
        if (u != null) {
          maintainers.add(new MaintainerVO(String.valueOf(uid), u.getRealName() != null ? u.getRealName() : u.getUsername()));
        }
      }
    }
    vo.setMaintainerList(maintainers);
    return vo;
  }
}
