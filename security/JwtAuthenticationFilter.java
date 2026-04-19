package com.alsystem.casemanager.security;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
/**
 * JWT 认证过滤器：解析 Authorization 头并写入 SecurityContext。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final UserDetailsService userDetailsService;

  /**
   * 构造函数。
   */
  public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
    this.jwtUtil = jwtUtil;
    this.userDetailsService = userDetailsService;
  }

  @Override
  /**
   * 过滤器主流程：解析 token -> 加载用户 -> 注入认证上下文。
   */
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // 提取 Bearer token。
    String header = request.getHeader("Authorization");
    String token = null;
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      token = header.substring(7);
    }
    if (StringUtils.hasText(token)) {
      try {
        // 解析用户名并加载用户详情。
        String username = jwtUtil.getUsername(token);
        UserDetails user = userDetailsService.loadUserByUsername(username);
        // 构造认证对象并写入上下文。
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (Exception ignored) {
        // token 异常时清理上下文，交给后续鉴权机制处理。
        SecurityContextHolder.clearContext();
      }
    }
    // 放行后续过滤器链。
    filterChain.doFilter(request, response);
  }
}
