package com.alsystem.casemanager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.alsystem.casemanager.mapper")
/**
 * 应用启动入口。
 *
 * <p>说明：
 * - {@link MapperScan} 用于扫描 MyBatis-Plus Mapper 接口；
 * - 其它配置（Security/Async/MyBatisPlus/Properties）由 Spring Boot 自动装配与组件扫描加载。
 */
public class CaseManagerApplication {

  /**
   * Java 主方法：启动 Spring Boot。
   *
   * @param args 启动参数
   */
  public static void main(String[] args) {
    SpringApplication.run(CaseManagerApplication.class, args);
  }
}
