package com.alsystem.casemanager.security;

import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
/**
 * Spring Security 主配置：JWT 无状态认证 + CORS + 放行登录注册接口。
 */
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

  /**
   * 构造函数：注入 JWT 过滤器与未认证处理器。
   */
  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
  }

  @Bean
  /**
   * 密码编码器 Bean。
   */
  public PasswordEncoder passwordEncoder() {
    return new PlainTextPasswordEncoder();
  }

  @Bean
  /**
   * 认证管理器 Bean。
   */
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    // 复用 Spring 提供的 AuthenticationManager。
    return config.getAuthenticationManager();
  }

  @Bean
  /**
   * 安全过滤链。
   */
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // 关闭 CSRF，启用 CORS，使用 JWT 无状态会话。
    http.csrf()
        .disable()    // 关闭 CSRF
        .cors()
        .and()  // 启用 CORS
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)   // 设置无状态会话（不使用 session）
        .and()
        .exceptionHandling()
        .authenticationEntryPoint(jwtAuthenticationEntryPoint)    // 认证失败返回401
        .and()
        .authorizeRequests()
        .antMatchers("/api/auth/login", "/api/auth/register")   // 允许无认证通过：登录、注册
        .permitAll()
        .antMatchers(HttpMethod.OPTIONS, "/**")   // 允许所有 OPTIONS 请求
        .permitAll()
        .anyRequest()
        .authenticated()
        .and()
        // 在用户名密码过滤器之前注入 JWT 解析过滤器。
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  /**
   * CORS 配置源。
   */
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOriginPatterns(Arrays.asList("*"));
    c.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    c.setAllowedHeaders(Arrays.asList("*"));
    c.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
