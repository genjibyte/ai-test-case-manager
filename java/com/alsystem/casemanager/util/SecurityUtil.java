package com.alsystem.casemanager.util;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alsystem.casemanager.entity.SysUser;
import com.alsystem.casemanager.mapper.SysUserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtil {

  private final SysUserMapper userMapper;

  /**
   * 构造函数：注入用户 Mapper。
   */
  public SecurityUtil(SysUserMapper userMapper) {
    this.userMapper = userMapper;
  }

  /**
   * 获取当前登录用户实体。
   */
  public SysUser currentUser() {
    // 从 SecurityContext 中获取当前认证主体。
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserDetails)) {
      throw new IllegalStateException("未登录");
    }
    // 读取用户名后从数据库查完整用户实体。
    String username = ((UserDetails) auth.getPrincipal()).getUsername();
    SysUser u =
        userMapper.selectOne(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
    if (u == null) {
      throw new IllegalStateException("用户不存在");
    }
    return u;
  }

  /**
   * 获取当前登录用户 ID。
   */
  public Long currentUserId() {
    return currentUser().getUserId();
  }

  /**
   * 判断用户是否为管理员账号。
   */
  public boolean isAdmin(SysUser user) {
    return "admin".equalsIgnoreCase(user.getUsername());
  }
}
