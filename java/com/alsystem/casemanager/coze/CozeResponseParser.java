package com.alsystem.casemanager.coze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alsystem.casemanager.vo.AiChatResponseVO;
import com.alsystem.casemanager.vo.AiGeneratedCaseVO;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析 Coze / 通用 JSON 中的 reply 与 cases 列表。 */
public final class CozeResponseParser {

  private CozeResponseParser() {}

  public static AiChatResponseVO parseJsonBody(
      String raw, String projectId, ObjectMapper objectMapper) {
    String fromRegex = extractResultStringFromRaw(raw);
    try {
      JsonNode root = objectMapper.readTree(raw);
      AiChatResponseVO vo = new AiChatResponseVO();
      String reply = extractReply(root);
      if (isTrivialReply(reply) && fromRegex != null && !fromRegex.trim().isEmpty()) {
        reply = fromRegex;
      }
      vo.setReply(reply.isEmpty() ? "（无文本内容）" : reply);
      vo.setCases(extractCases(root, projectId));
      return vo;
    } catch (Exception e) {
      AiChatResponseVO vo = new AiChatResponseVO();
      if (fromRegex != null && !fromRegex.trim().isEmpty()) {
        vo.setReply(fromRegex);
      } else {
        vo.setReply(raw);
      }
      vo.setCases(new ArrayList<>());
      return vo;
    }
  }

  public static AiChatResponseVO buildFromStream(
      StringBuilder accumulatedText,
      List<JsonNode> dataNodes,
      String projectId,
      ObjectMapper objectMapper) {
    AiChatResponseVO vo = new AiChatResponseVO();
    JsonNode bestForCases = findBestNodeForCases(dataNodes);
    String accumulation = accumulatedText.toString().trim();
    String fromRegex = extractResultStringFromRaw(accumulation);
    String reply = accumulation;
    if (!reply.isEmpty()) {
      try {
        JsonNode acc = objectMapper.readTree(reply);
        reply = extractReply(acc);
      } catch (Exception ignored) {
        /* 累积片段非完整 JSON 时保留原文 */
      }
    }
    if (isTrivialReply(reply) && fromRegex != null && !fromRegex.trim().isEmpty()) {
      reply = fromRegex;
    }
    if (isTrivialReply(reply) && bestForCases != null) {
      reply = extractReply(bestForCases);
    }
    if (isTrivialReply(reply)) {
      reply = bestReplyFromDataNodes(dataNodes);
    }
    vo.setReply(reply.isEmpty() ? "（无文本内容）" : reply);
    if (bestForCases != null) {
      vo.setCases(extractCases(bestForCases, projectId));
    } else {
      vo.setCases(new ArrayList<>());
    }
    return vo;
  }

  private static JsonNode findBestNodeForCases(List<JsonNode> dataNodes) {
    for (int i = dataNodes.size() - 1; i >= 0; i--) {
      JsonNode n = dataNodes.get(i);
      if (hasCasesArray(n)) {
        return n;
      }
    }
    return null;
  }

  /** 流式末尾常出现空 <code>{}</code> 事件，不能当作有效正文 */
  private static boolean isTrivialReply(String reply) {
    if (reply == null) {
      return true;
    }
    String t = reply.trim();
    return t.isEmpty() || "{}".equals(t) || "null".equalsIgnoreCase(t);
  }

  /**
   * 从后往前找第一条非空回复；避免最后一条 SSE 为 <code>{}</code> 时覆盖掉含 result 的节点。
   */
  private static String bestReplyFromDataNodes(List<JsonNode> dataNodes) {
    if (dataNodes == null || dataNodes.isEmpty()) {
      return "";
    }
    String longest = "";
    for (int i = dataNodes.size() - 1; i >= 0; i--) {
      String r = extractReply(dataNodes.get(i));
      if (!isTrivialReply(r)) {
        return r;
      }
      if (r != null && r.length() > longest.length()) {
        longest = r;
      }
    }
    return longest;
  }

  private static boolean hasCasesArray(JsonNode n) {
    if (n == null) {
      return false;
    }
    if (n.has("cases") && n.get("cases").isArray()) {
      return true;
    }
    return n.has("data")
        && n.get("data").has("cases")
        && n.get("data").get("cases").isArray();
  }

  public static String extractReply(JsonNode root) {
    if (root == null || root.isNull()) {
      return "";
    }
    if (root.isObject() && root.size() == 0) {
      return "";
    }
    if (root.has("reply") && root.get("reply").isTextual()) {
      return root.get("reply").asText();
    }
    if (root.has("result")) {
      String fromResult = textFromResultValue(root.get("result"));
      if (fromResult != null) {
        return fromResult;
      }
    }
    if (root.has("message") && root.get("message").isTextual()) {
      return root.get("message").asText();
    }
    if (root.has("data")) {
      JsonNode d = root.get("data");
      if (d != null && !d.isNull()) {
        if (d.has("reply") && d.get("reply").isTextual()) {
          return d.get("reply").asText();
        }
        if (d.has("result")) {
          String fromResult = textFromResultValue(d.get("result"));
          if (fromResult != null) {
            return fromResult;
          }
        }
        if (d.has("output") && d.get("output").isTextual()) {
          return d.get("output").asText();
        }
        if (d.has("content")) {
          String c = CozeContentExtractor.coerceContentText(d, "content");
          if (c != null && !c.trim().isEmpty()) {
            return c;
          }
        }
      }
    }
    if (root.has("choices")
        && root.get("choices").isArray()
        && root.get("choices").size() > 0) {
      JsonNode c0 = root.get("choices").get(0);
      if (c0.has("message") && c0.get("message").has("content")) {
        return c0.get("message").get("content").asText();
      }
    }
    if (root.has("content") && root.get("content").isTextual()) {
      return root.get("content").asText();
    }
    return root.toString();
  }

  /** result 可能为字符串，或嵌套对象（如含 output / content） */
  private static String textFromResultValue(JsonNode result) {
    if (result == null || result.isNull()) {
      return null;
    }
    if (result.isTextual()) {
      return result.asText();
    }
    if (result.isObject()) {
      if (result.has("output") && result.get("output").isTextual()) {
        return result.get("output").asText();
      }
      if (result.has("content") && result.get("content").isTextual()) {
        return result.get("content").asText();
      }
      if (result.has("text") && result.get("text").isTextual()) {
        return result.get("text").asText();
      }
      return result.toString();
    }
    if (result.isArray()) {
      return result.toString();
    }
    return null;
  }

  public static List<AiGeneratedCaseVO> extractCases(JsonNode root, String projectId) {
    List<AiGeneratedCaseVO> list = new ArrayList<>();
    JsonNode arr = null;
    if (root.has("cases") && root.get("cases").isArray()) {
      arr = root.get("cases");
    } else if (root.has("data") && root.get("data").has("cases")) {
      arr = root.get("data").get("cases");
    }
    if (arr == null || !arr.isArray()) {
      return list;
    }
    for (JsonNode n : arr) {
      AiGeneratedCaseVO vo = mapCaseNode(n, projectId);
      if (vo != null) {
        list.add(vo);
      }
    }
    return list;
  }

  private static AiGeneratedCaseVO mapCaseNode(JsonNode n, String projectId) {
    String name = text(n, "caseName", "case_name");
    if (name == null || name.isEmpty()) {
      return null;
    }
    AiGeneratedCaseVO vo = new AiGeneratedCaseVO();
    vo.setTempId(UUID.randomUUID().toString());
    vo.setProjectId(projectId);
    vo.setCaseName(name);
    String code = text(n, "caseCode", "case_code");
    if (code == null || code.isEmpty()) {
      code = "AI-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);
    }
    vo.setCaseCode(code);
    vo.setCaseLevel(intOr(n, "caseLevel", "case_level", 3));
    vo.setCaseStatus(intOr(n, "caseStatus", "case_status", 1));
    String mind = text(n, "mindMapJson", "mind_map_json");
    vo.setMindMapJson(mind);
    return vo;
  }

  private static String text(JsonNode n, String a, String b) {
    if (n.has(a) && !n.get(a).isNull()) {
      return n.get(a).asText();
    }
    if (b != null && n.has(b) && !n.get(b).isNull()) {
      return n.get(b).asText();
    }
    return null;
  }

  private static int intOr(JsonNode n, String a, String b, int def) {
    if (n.has(a) && !n.get(a).isNull()) {
      return n.get(a).asInt(def);
    }
    if (b != null && n.has(b) && !n.get(b).isNull()) {
      return n.get(b).asInt(def);
    }
    return def;
  }

  private static final Pattern RESULT_STRING_KEY = Pattern.compile("\"result\"\\s*:\\s*\"");

  /**
   * 与前端一致：用正则定位 <code>"result":"</code>，再按 JSON 字符串转义规则扫到闭合引号，取最长匹配（应对
   * <code>}{}{}{"result":"...</code> 等无法整体 parse 的拼接）。
   */
  static String extractResultStringFromRaw(String raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    Matcher m = RESULT_STRING_KEY.matcher(raw);
    String best = null;
    while (m.find()) {
      int openQuote = m.end() - 1;
      String s = parseJsonStringLiteralFromOpenQuote(raw, openQuote);
      if (!s.isEmpty() && (best == null || s.length() > best.length())) {
        best = s;
      }
    }
    return best;
  }

  private static String parseJsonStringLiteralFromOpenQuote(String raw, int openQuoteIndex) {
    if (openQuoteIndex < 0 || openQuoteIndex >= raw.length() || raw.charAt(openQuoteIndex) != '"') {
      return "";
    }
    StringBuilder out = new StringBuilder();
    int i = openQuoteIndex + 1;
    while (i < raw.length()) {
      char c = raw.charAt(i);
      if (c == '\\') {
        if (i + 1 >= raw.length()) {
          break;
        }
        char n = raw.charAt(i + 1);
        switch (n) {
          case '"':
            out.append('"');
            i += 2;
            continue;
          case '\\':
            out.append('\\');
            i += 2;
            continue;
          case '/':
            out.append('/');
            i += 2;
            continue;
          case 'b':
            out.append('\b');
            i += 2;
            continue;
          case 'f':
            out.append('\f');
            i += 2;
            continue;
          case 'n':
            out.append('\n');
            i += 2;
            continue;
          case 'r':
            out.append('\r');
            i += 2;
            continue;
          case 't':
            out.append('\t');
            i += 2;
            continue;
          case 'u':
            if (i + 5 < raw.length()) {
              String hex = raw.substring(i + 2, i + 6);
              try {
                out.append((char) Integer.parseInt(hex, 16));
                i += 6;
                continue;
              } catch (NumberFormatException ignored) {
              }
            }
            out.append(n);
            i += 2;
            continue;
          default:
            out.append(n);
            i += 2;
            continue;
        }
      }
      if (c == '"') {
        return out.toString();
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }
}
