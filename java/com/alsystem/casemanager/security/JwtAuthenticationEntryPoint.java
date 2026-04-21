package com.alsystem.casemanager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alsystem.casemanager.common.Result;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
/**
 * JWT 认证入口：未登录或 token 无效时返回统一 401 JSON。
 */
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  /**
   * 未认证处理回调。
   */
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    // 统一返回 401 与标准错误体，便于前端拦截处理。
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getOutputStream(), Result.fail(401, "未登录或登录已过期"));
  }
}
