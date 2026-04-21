package com.alsystem.casemanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
/**
 * JWT 配置属性。
 */
public class JwtProperties {

  /** 签名密钥。 */
  private String secret;
  /** 过期秒数。 */
  private long expireSeconds = 7200;
}
