package com.alsystem.casemanager.security;

import com.alsystem.casemanager.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
/**
 * JWT 工具类：签发、解析和提取业务声明。
 */
public class JwtUtil {

  private final JwtProperties props;

  /**
   * 构造函数。
   */
  public JwtUtil(JwtProperties props) {
    this.props = props;
  }

  /**
   * 构建签名密钥。
   */
  private SecretKey key() {
    byte[] bytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(bytes);
  }

  /**
   * 创建 token。
   */
  public String createToken(String username, Long userId) {
    long exp = props.getExpireSeconds() * 1000L;
    return Jwts.builder()
        .setSubject(username)
        .claim("uid", userId)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + exp))
        .signWith(key(), SignatureAlgorithm.HS256)
        .compact();
  }

  /**
   * 解析 token 声明体。
   */
  public Claims parse(String token) {
    return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
  }

  /**
   * 获取用户名。
   */
  public String getUsername(String token) {
    return parse(token).getSubject();
  }

  /**
   * 获取用户 ID。
   */
  public Long getUserId(String token) {
    Object uid = parse(token).get("uid");
    if (uid instanceof Integer) {
      return ((Integer) uid).longValue();
    }
    if (uid instanceof Long) {
      return (Long) uid;
    }
    return Long.parseLong(uid.toString());
  }
}
