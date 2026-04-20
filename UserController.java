package com.alsystem.casemanager.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.entity.SysUser;
import com.alsystem.casemanager.mapper.SysUserMapper;
import com.alsystem.casemanager.vo.UserSimpleVO;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

  private final SysUserMapper userMapper;

  /**
   * 构造函数：注入用户 Mapper。
   */
  public UserController(SysUserMapper userMapper) {
    this.userMapper = userMapper;
  }

  /**
   * 获取可用用户列表（用于项目维护人选择等场景）。
   */
  @GetMapping("/list")
  public Result<List<UserSimpleVO>> list() {
    // 查询所有启用状态的用户并按用户名排序。
    List<SysUser> users =
        userMapper.selectList(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStatus, 1).orderByAsc(SysUser::getUsername));
    // 将数据库实体映射为前端轻量展示对象。
    List<UserSimpleVO> list =
        users.stream()
            .map(
                u ->
                    new UserSimpleVO(
                        String.valueOf(u.getUserId()),
                        u.getUsername(),
                        u.getRealName() != null ? u.getRealName() : u.getUsername()))
            .collect(Collectors.toList());
    return Result.ok(list);
  }
}
