package com.alsystem.casemanager.coze;

import com.fasterxml.jackson.databind.JsonNode;

/** 从 Coze 流式 JSON 事件中尽量抽取增量文本（兼容多种字段路径）。 */
public final class CozeContentExtractor {

  private CozeContentExtractor() {}

  /**
   * v3 流式常见形态：<code>{"event":"conversation.message.delta","data":{"content":"…"}}</code>；{@code content} 也可能是数组或多模态对象。
   */
  public static String extractDeltaText(JsonNode n) {
    if (n == null || n.isNull()) {
      return null;
    }
    JsonNode data = n.get("data");
    if (data != null && !data.isNull() && data.isObject()) {
      String t = deltaFromReasoningOrContent(data);
      if (t != null && !t.isEmpty()) {
        return t;
      }
      t = coerceContentText(data.path("message"), "content");
      if (t != null && !t.isEmpty()) {
        return t;
      }
    }
    String t = deltaFromReasoningOrContent(n);
    if (t != null && !t.isEmpty()) {
      return t;
    }
    t = tryText(n, "result");
    if (t != null) {
      return t;
    }
    t = coerceContentText(n.path("message"), "content");
    if (t != null && !t.isEmpty()) {
      return t;
    }
    t = coerceContentText(n.path("delta"), "content");
    if (t != null && !t.isEmpty()) {
      return t;
    }
    if (data != null && !data.isNull()) {
      t = tryText(data, "result");
      if (t != null) {
        return t;
      }
      t = tryText(data, "output");
      if (t != null) {
        return t;
      }
      t = coerceContentText(data.path("delta"), "content");
      if (t != null && !t.isEmpty()) {
        return t;
      }
      if (data.has("delta") && data.get("delta").isTextual()) {
        return data.get("delta").asText();
      }
    }
    JsonNode choices = n.get("choices");
    if (choices != null && choices.isArray() && choices.size() > 0) {
      JsonNode c0 = choices.get(0);
      t = coerceContentText(c0.path("delta"), "content");
      if (t != null && !t.isEmpty()) {
        return t;
      }
    }
    return null;
  }

  /**
   * 会话消息完成事件中的完整正文（<code>conversation.message.completed</code>），{@code type} 常为 {@code answer}。
   */
  public static String extractCompletedAnswerText(JsonNode n) {
    if (n == null || n.isNull()) {
      return null;
    }
    String event = n.path("event").asText("");
    JsonNode data = n.get("data");
    if (data != null && !data.isNull() && data.isObject()) {
      String type = data.path("type").asText("");
      if (type.isEmpty()
          || "answer".equalsIgnoreCase(type)
          || "assistant".equalsIgnoreCase(type)) {
        String full = coerceContentText(data, "content");
        if (full != null && !full.isEmpty()) {
          return full;
        }
      }
      String full = coerceContentText(data.path("message"), "content");
      if (full != null && !full.isEmpty()) {
        return full;
      }
    }
    if ("conversation.message.completed".equals(event)) {
      String type = n.path("type").asText("");
      if (type.isEmpty()
          || "answer".equalsIgnoreCase(type)
          || "assistant".equalsIgnoreCase(type)) {
        String full = coerceContentText(n, "content");
        if (full != null && !full.isEmpty()) {
          return full;
        }
      }
      String full = coerceContentText(n.path("message"), "content");
      if (full != null && !full.isEmpty()) {
        return full;
      }
    }
    return null;
  }

  private static String deltaFromReasoningOrContent(JsonNode parent) {
    if (parent == null || parent.isNull()) {
      return null;
    }
    String r = coerceContentText(parent, "reasoning_content");
    if (r != null && !r.isEmpty()) {
      return r;
    }
    return coerceContentText(parent, "content");
  }

  /** content / reasoning_content：可为字符串、多模态数组或对象 */
  public static String coerceContentText(JsonNode parent, String field) {
    if (parent == null || parent.isNull() || !parent.has(field)) {
      return null;
    }
    return nodeToPlainText(parent.get(field));
  }

  private static String nodeToPlainText(JsonNode v) {
    if (v == null || v.isNull()) {
      return null;
    }
    if (v.isTextual()) {
      String t = v.asText();
      return t.isEmpty() ? null : t;
    }
    if (v.isArray()) {
      StringBuilder sb = new StringBuilder();
      for (JsonNode item : v) {
        if (item == null || item.isNull()) {
          continue;
        }
        if (item.has("text") && item.get("text").isTextual()) {
          sb.append(item.get("text").asText());
        } else if (item.isTextual()) {
          sb.append(item.asText());
        } else if (item.has("content")) {
          String inner = nodeToPlainText(item.get("content"));
          if (inner != null) {
            sb.append(inner);
          }
        }
      }
      return sb.length() > 0 ? sb.toString() : null;
    }
    if (v.isObject()) {
      if (v.has("text") && v.get("text").isTextual()) {
        String t = v.get("text").asText();
        return t.isEmpty() ? null : t;
      }
      if (v.has("content")) {
        return nodeToPlainText(v.get("content"));
      }
    }
    return null;
  }

  private static String tryText(JsonNode n, String field) {
    if (n == null || n.isNull() || !n.has(field)) {
      return null;
    }
    JsonNode v = n.get(field);
    if (v.isTextual()) {
      return v.asText();
    }
    return null;
  }
}
