package com.alsystem.casemanager.coze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 解析 text/event-stream：空行分隔事件；同一事件内多行 data: 用换行拼接（SSE 规范）。
 *
 * <p>扣子等平台可能用独立行 {@code event: conversation.message.delta}，而 {@code data:} 内仅为 JSON 体（无
 * event 字段）；此类情况下将 event 名并入 JSON，供后续解析。
 */
public final class CozeSseLineParser {

  private static final ObjectMapper OM = new ObjectMapper();

  private CozeSseLineParser() {}

  public static void forEachDataPayload(InputStream in, Consumer<String> onDataJson) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    StringBuilder dataBuf = new StringBuilder();
    String pendingEvent = null;
    String line;
    while ((line = br.readLine()) != null) {
      if (line.isEmpty()) {
        flush(dataBuf, onDataJson, pendingEvent);
        pendingEvent = null;
        continue;
      }
      if (line.startsWith("event:")) {
        pendingEvent = line.substring(6).trim();
        continue;
      }
      if (line.startsWith("data:")) {
        String part = line.substring(5).trim();
        if (dataBuf.length() > 0) {
          dataBuf.append('\n');
        }
        dataBuf.append(part);
      }
    }
    flush(dataBuf, onDataJson, pendingEvent);
  }

  private static void flush(StringBuilder dataBuf, Consumer<String> onDataJson, String sseEventName) {
    if (dataBuf.length() == 0) {
      return;
    }
    String s = dataBuf.toString().trim();
    dataBuf.setLength(0);
    if (s.isEmpty() || "[DONE]".equalsIgnoreCase(s)) {
      return;
    }
    if (sseEventName != null && !sseEventName.isEmpty()) {
      try {
        JsonNode n = OM.readTree(s);
        if (n.isObject() && !n.has("event")) {
          ((ObjectNode) n).put("event", sseEventName);
          s = OM.writeValueAsString(n);
        }
      } catch (Exception ignored) {
        // 非 JSON 时原样传递
      }
    }
    onDataJson.accept(s);
  }
}
