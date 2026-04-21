package com.alsystem.casemanager.coze;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 从 Coze SSE/JSON 事件中提取 {@code conversation_id}。
 *
 * <p>用途：
 * - Agent 多轮对话需要携带上游 {@code conversation_id} 才能续聊；
 * - 我们把它落库到 {@code ai_chat_session.coze_conversation_id}，后续请求带 {@code sessionId} 即可自动续聊。
 *
 * <p>兼容性：
 * - conversation_id 可能出现在根节点、data 节点、message 节点等不同位置；
 * - 该类按常见路径依次尝试，取第一处命中。
 */
public final class CozeConversationIdExtractor {

  private CozeConversationIdExtractor() {}

  /**
   * 尝试抽取 conversation_id。
   *
   * @param node 单条 SSE data 对应的 JSON 节点
   * @return conversation_id 或 null
   */
  public static String extract(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    // 1) 最常见：根节点直接携带 conversation_id。
    String t = text(node, "conversation_id");
    if (t != null) {
      return t;
    }
    // 2) 其次：data 对象中携带。
    JsonNode d = node.get("data");
    if (d != null && d.isObject()) {
      t = text(d, "conversation_id");
      if (t != null) {
        return t;
      }
    }
    // 3) 兼容：message 对象中携带。
    JsonNode msg = node.get("message");
    if (msg != null && msg.isObject()) {
      t = text(msg, "conversation_id");
      if (t != null) {
        return t;
      }
    }
    return null;
  }

  /**
   * 安全读取文本字段。
   */
  private static String text(JsonNode n, String field) {
    if (n.has(field) && n.get(field).isTextual()) {
      return n.get(field).asText();
    }
    return null;
  }
}
