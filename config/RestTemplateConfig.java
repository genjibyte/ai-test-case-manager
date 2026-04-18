package com.alsystem.casemanager.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
/**
 * RestTemplate 配置。
 */
public class RestTemplateConfig {

  @Bean
  /**
   * 创建 RestTemplate 并配置连接/读取超时。
   */
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofSeconds(30))
        .setReadTimeout(Duration.ofMinutes(2))
        .build();
  }
}
