package com.alsystem.casemanager.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alsystem.casemanager.entity.SysUser;
import com.alsystem.casemanager.mapper.SysUserMapper;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
/**
 * 自定义用户详情服务：从数据库加载用户并适配为 Spring Security 用户对象。
 */
public class CustomUserDetailsService implements UserDetailsService {

  private final SysUserMapper userMapper;

  /**
   * 构造函数。
   */
  public CustomUserDetailsService(SysUserMapper userMapper) {
    this.userMapper = userMapper;
  }

  @Override
  /**
   * 按用户名加载用户详情。
   */
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // 查询数据库用户。
    SysUser u =
        userMapper.selectOne(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
    if (u == null) {
      throw new UsernameNotFoundException(username);
    }
    // 转换为 Security UserDetails。
    return User.withUsername(u.getUsername())
        .password(u.getPassword())
        .authorities("ROLE_USER")
        .disabled(u.getStatus() != null && u.getStatus() == 0)
        .build();
  }
}
