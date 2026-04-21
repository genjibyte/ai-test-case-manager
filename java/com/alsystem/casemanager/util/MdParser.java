package com.alsystem.casemanager.util;

import java.util.ArrayList;
import java.util.List;

/** 严格列表解析：仅支持空格缩进 + "- "，每深一层缩进 +2 空格。 */
public final class MdParser {

  /**
   * 工具类不允许实例化。
   */
  private MdParser() {}

  /**
   * 列表标题结构：记录层级与文本。
   */
  public static final class Title {
    private final int level;
    private final String text;

    /**
     * 构造标题对象。
     */
    public Title(int level, String text) {
      this.level = level;
      this.text = text;
    }

    /**
     * 获取层级（从 1 开始）。
     */
    public int getLevel() {
      return level;
    }

    /**
     * 获取文本内容。
     */
    public String getText() {
      return text;
    }
  }

  /**
   * 解析 Markdown 列表为层级标题集合。
   */
  public static List<Title> parse(String md) {
    List<Title> out = new ArrayList<>();
    if (md == null || md.trim().isEmpty()) {
      return out;
    }
    // 逐行解析，严格约束缩进与列表标记，确保后续树结构稳定。
    String[] lines = md.split("\\r?\\n");
    for (int i = 0; i < lines.length; i++) {
      String raw = lines[i];
      if (raw == null) {
        continue;
      }
      // 当前实现明确禁止 Tab 缩进，避免混用导致层级歧义。
      if (raw.indexOf('\t') >= 0) {
        throw new IllegalArgumentException("第 " + (i + 1) + " 行包含 Tab，仅允许空格缩进");
      }
      String r = trimRight(raw);
      // 忽略空白行。
      if (r.trim().isEmpty()) {
        continue;
      }
      int leadingSpaces = countLeadingSpaces(r);
      // 每层要求 2 个空格，奇数空格直接判为非法输入。
      if ((leadingSpaces % 2) != 0) {
        throw new IllegalArgumentException("第 " + (i + 1) + " 行缩进不是 2 的倍数");
      }
      String body = r.substring(leadingSpaces);
      // 只支持 "- " 列表项，保持格式一致性。
      if (!body.startsWith("- ")) {
        throw new IllegalArgumentException("第 " + (i + 1) + " 行不是合法列表项（需以 '- ' 开头）");
      }
      String text = body.substring(2).trim();
      // 文本不允许为空，避免出现空节点。
      if (text.isEmpty()) {
        throw new IllegalArgumentException("第 " + (i + 1) + " 行列表文本不能为空");
      }
      // 层级计算：0 缩进为 level=1。
      int level = leadingSpaces / 2 + 1;
      out.add(new Title(level, text));
    }
    return out;
  }

  /**
   * 统计字符串左侧连续空格数。
   */
  private static int countLeadingSpaces(String s) {
    int n = 0;
    while (n < s.length() && s.charAt(n) == ' ') {
      n++;
    }
    return n;
  }

  /**
   * 去除右侧空格（保留左侧缩进用于层级判断）。
   */
  private static String trimRight(String s) {
    int end = s.length() - 1;
    while (end >= 0 && s.charAt(end) == ' ') {
      end--;
    }
    return end < 0 ? "" : s.substring(0, end + 1);
  }
}

