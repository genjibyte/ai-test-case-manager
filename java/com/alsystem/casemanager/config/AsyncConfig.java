package com.alsystem.casemanager.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
/**
 * 异步线程池配置。
 */
public class AsyncConfig {

  @Bean(name = "aiTaskExecutor")
  /**
   * AI 任务线程池：用于 Coze 流式请求转发等耗时任务。
   */
  public Executor aiTaskExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(2);
    e.setMaxPoolSize(16);
    e.setQueueCapacity(50);
    e.setThreadNamePrefix("coze-ai-");
    e.initialize();
    return e;
  }
}
