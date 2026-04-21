package com.alsystem.casemanager.coze;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StringUtils;

/**
 * Coze Open API 低层 HTTP 工具。
 *
 * <p>职责边界：
 * - 仅负责“发起 HTTP POST(JSON)”与“读取响应流为 UTF-8 字符串”；
 * - 不负责业务参数拼装、不负责 JSON 解析、不负责 SSE 事件语义处理。
 *
 * <p>鉴权：
 * - 默认使用 {@code Authorization: Bearer <token>}；
 * - 如上游要求自定义 header，则使用 {@code headerName} 直接写入。
 */
public final class CozeHttpUtils {

  /** 工作流等长耗时调用：读取超时 1 小时 */
  public static final int READ_TIMEOUT_WORKFLOW_MS = 3_600_000;

  /** Agent 等流式调用：读取超时 10 分钟 */
  public static final int READ_TIMEOUT_AGENT_MS = 600_000;

  private CozeHttpUtils() {}

  /**
   * 打开一个 POST(JSON) 连接，读取超时按 Agent 默认值。
   *
   * @param token 密钥或完整 Header 值（由 headerName 决定如何设置）
   */
  public static HttpURLConnection openPostJson(
      String urlStr, String jsonBody, String token, String headerName) throws IOException {
    // 默认按 Agent 的流式读取超时。
    return openPostJson(urlStr, jsonBody, token, headerName, READ_TIMEOUT_AGENT_MS);
  }

  /**
   * 打开一个 POST(JSON) 连接，并写入请求体。
   *
   * @param readTimeoutMs 读取超时（毫秒），工作流长任务建议 {@link #READ_TIMEOUT_WORKFLOW_MS}
   */
  public static HttpURLConnection openPostJson(
      String urlStr,
      String jsonBody,
      String token,
      String headerName,
      int readTimeoutMs)
      throws IOException {
    // 1) 建立连接并配置基础属性。
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setConnectTimeout(30_000);
    conn.setReadTimeout(readTimeoutMs);
    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    // 2) 设置鉴权 Header（可选）。
    if (StringUtils.hasText(token) && StringUtils.hasText(headerName)) {
      if ("Authorization".equalsIgnoreCase(headerName.trim())) {
        // 约定：Authorization 头则自动补 Bearer 前缀。
        conn.setRequestProperty("Authorization", "Bearer " + token.trim());
      } else {
        // 其它 headerName：原样写入（由调用方保证格式）。
        conn.setRequestProperty(headerName.trim(), token.trim());
      }
    }
    // 3) 写入请求体。使用 fixed-length streaming，避免内部缓冲导致的内存占用。
    byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
    conn.setFixedLengthStreamingMode(bytes.length);
    try (OutputStream os = conn.getOutputStream()) {
      os.write(bytes);
    }
    return conn;
  }

  /**
   * 将输入流完整读取为 UTF-8 字符串。
   *
   * <p>典型用途：
   * - 上游返回非 SSE（application/json）时读取 body
   * - 上游返回错误时读取 error stream，便于拼装错误提示
   */
  public static String readUtf8Stream(InputStream in) throws IOException {
    if (in == null) {
      return "";
    }
    // 逐块读取，避免对大响应一次性分配超大数组。
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) {
      out.write(buf, 0, n);
    }
    return out.toString(StandardCharsets.UTF_8.name());
  }
}
