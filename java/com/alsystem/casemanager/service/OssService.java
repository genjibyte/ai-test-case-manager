package com.alsystem.casemanager.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.alsystem.casemanager.config.OssProperties;
import java.io.InputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OssService implements DisposableBean {

  /** 单文件最大大小（字节）。这里与 application.yml 的 multipart 限制保持一致为 50MB。 */
  private static final long MAX_BYTES = 50L * 1024 * 1024;

  /** OSS 配置属性（endpoint / ak / sk / bucket / objectPrefix / publicUrlPrefix）。 */
  private final OssProperties props;
  /** OSS SDK 客户端实例（延迟初始化 + 复用）。 */
  private volatile OSS oss;

  /**
   * 构造函数：注入 OSS 配置属性。
   *
   * @param props OSS 配置（来自 application.yml 的 alsystem.oss 前缀）
   */
  public OssService(OssProperties props) {
    // 调用：保存配置引用，后续生成 client/URL 时都会用到
    this.props = props;
  }

  /**
   * 获取 OSS 客户端实例（延迟初始化 + 单例复用）。
   *
   * <p>为什么要延迟初始化：
   * - 本地/测试环境可能不启用 OSS，避免启动时就创建连接
   * - 需要先校验配置完整性，避免产生难定位的 SDK 异常
   *
   * <p>线程安全：
   * - 双重检查 + synchronized，保证并发场景只初始化一次
   *
   * @return OSS SDK 客户端
   */
  private OSS client() {
    // 校验：是否启用 OSS（未启用直接拒绝，避免误调用）
    if (!props.isEnabled()) {
      throw new IllegalArgumentException(
          "OSS 未启用：请在 alsystem.oss 中设置 enabled=true，并填写 endpoint、access-key-id、access-key-secret、bucket-name（详见项目内 OSS配置说明.md）");
    }
    // 校验：关键配置是否完整（endpoint/ak/sk/bucket）
    if (!StringUtils.hasText(props.getEndpoint())
        || !StringUtils.hasText(props.getAccessKeyId())
        || !StringUtils.hasText(props.getAccessKeySecret())
        || !StringUtils.hasText(props.getBucketName())) {
      throw new IllegalArgumentException(
          "OSS 配置不完整：请填写 alsystem.oss.endpoint、access-key-id、access-key-secret、bucket-name");
    }
    // 快速路径：已初始化则直接返回
    if (oss != null) {
      return oss;
    }
    // 慢路径：并发下只允许一个线程初始化
    synchronized (this) {
      if (oss == null) {
        // 调用：读取 endpoint 并做协议补全（OSS SDK 接受含协议的 endpoint）
        String ep = props.getEndpoint().trim();
        if (!ep.startsWith("http://") && !ep.startsWith("https://")) {
          ep = "https://" + ep;
        }
        // 调用：构建 OSS 客户端（ak/sk 取 trim 以避免环境变量多余空格导致鉴权失败）
        oss =
            new OSSClientBuilder()
                .build(ep, props.getAccessKeyId().trim(), props.getAccessKeySecret().trim());
      }
      // 返回：初始化后的单例客户端
      return oss;
    }
  }

  /**
   * 上传文件并返回公网访问地址。
   *
   * <p>这是一个便捷重载：不指定业务前缀目录，直接走默认 object-prefix + 日期目录归档。
   *
   * @param file 上传文件
   * @return 可访问 URL（public-url-prefix 优先，否则按 bucket.endpoint 拼接）
   */
  public String upload(MultipartFile file) {
    // 调用：复用带 keyPrefix 的实现（keyPrefix=null）
    return upload(file, null);
  }

  /**
   * 上传文件到指定前缀目录（会在系统 object-prefix 下归档），并返回公网访问地址。
   *
   * <p>例如传入 keyPrefix = "project/123/folder/a/b"，最终对象键将形如：
   * {objectPrefix}/project/123/folder/a/b/yyyy/MM/dd/uuid-name.ext
   *
   * @param file 上传文件（multipart）
   * @param keyPrefix 业务归档前缀（可空）。该前缀会被规范化，禁止包含 .. 等危险片段。
   * @return 上传后的可访问 URL
   */
  public String upload(MultipartFile file, String keyPrefix) {
    // ========= 1) 入参校验 =========
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("文件为空");
    }
    // 校验：文件大小（字节）
    if (file.getSize() > MAX_BYTES) {
      throw new IllegalArgumentException("文件不能超过 50MB");
    }
    // ========= 2) 规范化文件名 =========
    // 调用：读取原始文件名（用于保留扩展名与可读性）
    String original = file.getOriginalFilename();
    if (!StringUtils.hasText(original)) {
      // 兜底：某些客户端可能不带文件名
      original = "file.bin";
    }
    // 调用：定位扩展名分隔符
    int dot = original.lastIndexOf('.');
    // 计算：扩展名（含 '.'）
    String ext = dot >= 0 ? original.substring(dot).toLowerCase() : "";
    // 计算：基础名（不含扩展名）
    String base = dot > 0 ? original.substring(0, dot) : original;
    // 调用：用白名单字符集替换非法字符，避免 OSS key 包含控制字符/特殊符号导致上传失败
    String safe = base.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    if (safe.isEmpty()) {
      // 兜底：全部字符被替换后为空，则给默认名
      safe = "file";
    }
    // ========= 3) 生成 OSS 对象键 key =========
    // 调用：按日期组织目录（便于运维清理/分区）
    String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
    // 读取：系统级对象前缀（默认 case-upload）
    String prefix = props.getObjectPrefix() != null ? props.getObjectPrefix().trim() : "case-upload";
    // 调用：移除多余斜杠，保证拼接稳定
    prefix = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    // 调用：规范化业务前缀（防止 ../ 路径穿越、压缩多余斜杠）
    String extraPrefix = normalizeKeyPrefix(keyPrefix);
    // 计算：最终 key 的 base（objectPrefix + 业务前缀）
    String keyBase = prefix + (extraPrefix.isEmpty() ? "" : ("/" + extraPrefix));
    // 调用：生成 UUID，拼接最终对象键（包含日期与随机前缀，避免重名覆盖）
    String key = keyBase + "/" + datePath + "/" + UUID.randomUUID().toString() + "-" + safe + ext;

    // ========= 4) 设置对象元数据 =========
    // 目的：让 OSS 返回更准确的 Content-Length/Content-Type，便于浏览器打开/下载
    ObjectMetadata meta = new ObjectMetadata();
    // 调用：设置对象长度
    meta.setContentLength(file.getSize());
    // 调用：若客户端提供了 ContentType，则写入
    if (StringUtils.hasText(file.getContentType())) {
      meta.setContentType(file.getContentType());
    }
    // ========= 5) 上传到 OSS =========
    // try-with-resources：确保 InputStream 关闭
    try (InputStream in = file.getInputStream()) {
      // 真正调用 OSS SDK 上传对象。
      // 调用：client() 获取 OSS SDK 客户端
      // 调用：putObject(bucket, key, stream, meta) 执行上传
      client().putObject(props.getBucketName().trim(), key, in, meta);
    } catch (IllegalArgumentException e) {
      // 业务异常原样抛出（由全局异常处理统一返回 400）
      throw e;
    } catch (Exception e) {
      // 其它异常统一包装为参数错误语义，避免泄露内部细节
      throw new IllegalArgumentException("OSS 上传失败：" + e.getMessage());
    }
    // ========= 6) 生成对外可访问 URL =========
    // 调用：按 public-url-prefix 或 bucket.endpoint 拼接 URL
    return buildPublicUrl(key);
  }

  /**
   * 将传入前缀规范化为 OSS 对象键的子路径：
   * - 去掉首尾斜杠
   * - 拒绝包含 .. 的路径片段
   * - 将连续斜杠压缩为单个
   */
  private String normalizeKeyPrefix(String keyPrefix) {
    // 校验：空前缀直接返回空串
    if (!StringUtils.hasText(keyPrefix)) {
      return "";
    }
    // 调用：trim 并把 Windows '\' 统一为 '/'
    String p = keyPrefix.trim().replace('\\', '/');
    // 调用：压缩多个连续 '/'
    p = p.replaceAll("/+", "/");
    // 调用：去除首尾 '/'
    p = p.replaceAll("^/+", "").replaceAll("/+$", "");
    if (!p.isEmpty()) {
      // 调用：按路径分段做安全校验
      String[] parts = p.split("/");
      for (String part : parts) {
        // 校验：禁止出现 .. 片段（避免路径穿越/越权写入其它目录）
        if ("..".equals(part) || part.contains("..")) {
          throw new IllegalArgumentException("非法上传路径");
        }
      }
    }
    // 返回：规范化后的前缀
    return p;
  }

  /** 按上传返回的公网 URL 删除对象（仅允许 object-prefix 下路径） */
  public void deleteByPublicUrl(String publicUrl) {
    // 校验：url 不能为空
    if (!StringUtils.hasText(publicUrl)) {
      throw new IllegalArgumentException("URL 为空");
    }
    // 先解析对象键，再做前缀白名单校验，避免误删其它路径文件。
    // 调用：从 URL 提取对象键 key
    String key = extractObjectKey(publicUrl.trim());
    // 调用：白名单校验 key 必须位于 objectPrefix 下
    assertKeyAllowed(key);
    try {
      // 删除 OSS 对象。
      // 调用：获取 OSS 客户端并执行 deleteObject
      client().deleteObject(props.getBucketName().trim(), key);
    } catch (IllegalArgumentException e) {
      // 业务异常原样抛出
      throw e;
    } catch (Exception e) {
      // 其它异常统一包装
      throw new IllegalArgumentException("OSS 删除失败：" + e.getMessage());
    }
  }

  /**
   * 从完整公网 URL 中提取 OSS 对象键。
   */
  private String extractObjectKey(String url) {
    try {
      // 调用：URI 解析（比字符串截取更稳健）
      URI u = new URI(url);
      // 调用：取 path（/prefix/xxx）
      String path = u.getPath();
      if (!StringUtils.hasText(path) || "/".equals(path)) {
        throw new IllegalArgumentException("无法从 URL 解析对象键");
      }
      // 计算：移除开头的 '/'
      return path.startsWith("/") ? path.substring(1) : path;
    } catch (IllegalArgumentException e) {
      // 业务异常原样抛出
      throw e;
    } catch (Exception e) {
      // 其它异常统一包装为“无效 URL”
      throw new IllegalArgumentException("无效的 URL：" + url);
    }
  }

  /**
   * 校验对象键必须位于系统可管理前缀下。
   */
  private void assertKeyAllowed(String key) {
    // 读取：系统 objectPrefix
    String prefix = props.getObjectPrefix() != null ? props.getObjectPrefix().trim() : "case-upload";
    // 调用：规范化前缀斜杠
    prefix = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    // 校验：只能删除系统前缀下的对象，避免越权删除其它业务文件
    if (!key.startsWith(prefix + "/") && !key.equals(prefix)) {
      throw new IllegalArgumentException("仅允许删除本系统上传路径下的文件");
    }
  }

  /**
   * 根据配置拼接对象公网访问地址。
   */
  private String buildPublicUrl(String key) {
    // 分支：若配置了自定义 CDN/域名，则优先使用该前缀拼接
    if (StringUtils.hasText(props.getPublicUrlPrefix())) {
      // 调用：去掉末尾 '/'，避免双斜杠
      return props.getPublicUrlPrefix().trim().replaceAll("/+$", "") + "/" + key;
    }
    // 兜底：使用 bucket.endpoint 的标准 URL 形式
    // 调用：从 endpoint 中移除协议，仅保留 host
    String ep = props.getEndpoint().trim().replace("https://", "").replace("http://", "");
    // 拼接：https://{bucket}.{endpointHost}/{key}
    return "https://" + props.getBucketName().trim() + "." + ep + "/" + key;
  }

  /**
   * Bean 销毁时释放 OSS 客户端资源。
   */
  @Override
  public void destroy() {
    // 调用：释放 OSS 客户端资源（连接池/线程等）
    if (oss != null) {
      // 调用：OSS SDK 的 shutdown()
      oss.shutdown();
      // 置空：便于 GC
      oss = null;
    }
  }
}
