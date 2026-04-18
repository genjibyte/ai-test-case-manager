package com.alsystem.casemanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alsystem.oss")
/**
 * OSS 相关配置属性。
 */
public class OssProperties {

  /** 是否启用上传（需同时填写 endpoint、密钥、Bucket） */
  private boolean enabled = false;

  /** 地域 Endpoint，如 https://oss-cn-shanghai.aliyuncs.com */
  private String endpoint = "";

  private String accessKeyId = "";
  private String accessKeySecret = "";
  private String bucketName = "";

  /** 对象键前缀，如 case-upload */
  private String objectPrefix = "case-upload";

  /**
   * 可选。若 Bucket 已绑定自定义域名/CDN，填完整前缀（无末尾 /），上传后返回的 URL 用此前缀拼接对象键。
   * 不填则使用：https://{bucket}.{endpointHost}/{key}
   */
  private String publicUrlPrefix = "";
}
