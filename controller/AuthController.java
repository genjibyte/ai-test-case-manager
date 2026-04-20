package com.alsystem.casemanager.controller;

import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.config.JwtProperties;
import com.alsystem.casemanager.dto.LoginDTO;
import com.alsystem.casemanager.dto.RegisterDTO;
import com.alsystem.casemanager.entity.SysUser;
import com.alsystem.casemanager.mapper.SysUserMapper;
import com.alsystem.casemanager.security.JwtUtil;
import com.alsystem.casemanager.util.SecurityUtil;
import com.alsystem.casemanager.vo.LoginVO;
import com.alsystem.casemanager.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collections;
import javax.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final SysUserMapper userMapper;
  private final JwtUtil jwtUtil;
  private final JwtProperties jwtProperties;
  private final SecurityUtil securityUtil;
  private final PasswordEncoder passwordEncoder;

  /**
   * 构造函数：注入认证所需组件。
   */
  public AuthController(
      AuthenticationManager authenticationManager,
      SysUserMapper userMapper,
      JwtUtil jwtUtil,
      JwtProperties jwtProperties,
      SecurityUtil securityUtil,
      PasswordEncoder passwordEncoder) {
    this.authenticationManager = authenticationManager;
    this.userMapper = userMapper;
    this.jwtUtil = jwtUtil;
    this.jwtProperties = jwtProperties;
    this.securityUtil = securityUtil;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * 用户注册并直接签发 token。
   */
  @PostMapping("/register")
  public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
    // 调用 Mapper 先检查用户名唯一性，避免重复注册。
    long exists =
        userMapper.selectCount(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()));
    if (exists > 0) {
      throw new IllegalArgumentException("用户名已存在");
    }
    // 组装并入库新用户实体。
    SysUser u = new SysUser();
    u.setUsername(dto.getUsername().trim());
    u.setPassword(passwordEncoder.encode(dto.getPassword()));
    u.setRealName(dto.getRealName() != null ? dto.getRealName().trim() : null);
    String rn = u.getRealName();
    if (rn != null && rn.isEmpty()) {
      u.setRealName(null);
    }
    u.setStatus(1);
    // 持久化新用户。
    userMapper.insert(u);
    // 生成 JWT，返回给前端存储。
    String token = jwtUtil.createToken(u.getUsername(), u.getUserId());
    LoginVO vo = new LoginVO();
    vo.setToken(token);
    vo.setRefreshToken(token);
    vo.setExpiresIn(jwtProperties.getExpireSeconds());
    // 复用内部转换方法构造用户信息。
    vo.setUserInfo(toUserInfo(u));
    return Result.ok("注册成功", vo);
  }

  /**
   * 用户登录：认证成功后签发 token。
   */
  @PostMapping("/login")
  public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
    // 通过 Spring Security 认证链校验用户名密码。
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
    // 认证成功后再读取用户资料，组装返回体。
    SysUser user =
        userMapper.selectOne(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()));
    if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
      throw new IllegalArgumentException("用户不可用");
    }
    // 签发访问令牌。
    String token = jwtUtil.createToken(user.getUsername(), user.getUserId());
    LoginVO vo = new LoginVO();
    vo.setToken(token);
    vo.setRefreshToken(token);
    vo.setExpiresIn(jwtProperties.getExpireSeconds());
    vo.setUserInfo(toUserInfo(user));
    return Result.ok("登录成功", vo);
  }

  /**
   * 登出接口：当前实现为无状态 JWT，主要用于前端语义化调用。
   */
  @PostMapping("/logout")
  public Result<Void> logout() {
    // 主动读取当前用户，用于校验当前请求是否为已登录上下文。
    securityUtil.currentUser();
    return Result.ok(null);
  }

  /**
   * 将用户实体转换为登录态用户信息 VO。
   */
  private UserInfoVO toUserInfo(SysUser user) {
    UserInfoVO vo = new UserInfoVO();
    vo.setUserId(String.valueOf(user.getUserId()));
    vo.setUsername(user.getUsername());
    vo.setRealName(user.getRealName());
    vo.setRoles(
        securityUtil.isAdmin(user)
            ? Collections.singletonList("admin")
            : Collections.singletonList("user"));
    vo.setPermissions(Collections.singletonList("*:*:*"));
    return vo;
  }
}
