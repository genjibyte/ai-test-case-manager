package com.alsystem.casemanager.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 解析 .xmind（zip）中的 content.json，并转换为 MindElixir 可直接渲染的数据结构（含 nodeData）。
 *
 * <p>说明：XMind 文件格式存在多个版本差异，本实现以读取 content.json 为主，并对字段做宽松兼容：
 * - 根节点：sheet.rootTopic
 * - 标题：topic.title
 * - 子节点：topic.children.attached 或 topic.children（数组）
 *
 * <p>输出格式：
 * - 返回 ObjectNode，至少包含字段 nodeData
 * - nodeData 结构满足前端 `MindMapEditor.vue` 的校验逻辑：只要 JSON 可 parse 且包含 nodeData，
 *   就可以直接调用 MindElixir.init/refresh 渲染
 *
 * <p>异常策略：
 * - 任何解析/读取异常均转换为 IllegalArgumentException（由全局异常处理器统一转成 400）
 */
@Component
public class XmindParser {

  private final ObjectMapper objectMapper;

  public XmindParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * 将 XMind 文件转换为 MindElixir 可渲染的数据 JSON（至少包含 nodeData）。
   *
   * @param xmindFile 上传的 .xmind 文件（本质是 zip）
   * @param fallbackRootTopic 当 XMind 内部缺少标题字段时，用该值作为根节点 topic（通常传入用例名）
   * @return 形如：{ "nodeData": { "id": "...", "topic": "...", "children": [...] } }
   */
  public ObjectNode parseToMindElixirData(MultipartFile xmindFile, String fallbackRootTopic) {
    // 入参兜底：文件为空直接报错（避免后续 zip/JSON 解析 NPE）
    if (xmindFile == null || xmindFile.isEmpty()) {
      throw new IllegalArgumentException("文件为空");
    }
    // 读取 zip 内的 content.json（XMind 主要内容）
    String contentJson = readZipEntryUtf8(xmindFile, "content.json");
    // 如果 content.json 不存在/为空，则认为不是可解析的 XMind 内容
    if (!StringUtils.hasText(contentJson)) {
      throw new IllegalArgumentException("XMind 文件缺少 content.json");
    }
    try {
      // 使用 Jackson 把 content.json 解析为 JsonNode 树，便于做多版本兼容读取
      JsonNode root = objectMapper.readTree(contentJson);
      // XMind content.json 常见结构：数组（多个 sheet），取第一个 sheet 作为主内容
      JsonNode sheet = root;
      if (root != null && root.isArray() && root.size() > 0) {
        // 取第一个 sheet 节点
        sheet = root.get(0);
      }
      // rootTopic 是常见版本下的根主题节点
      JsonNode rootTopic = sheet != null ? sheet.path("rootTopic") : null;
      if (rootTopic == null || rootTopic.isMissingNode() || rootTopic.isNull()) {
        // 兼容某些格式：直接把 sheet 当 rootTopic
        rootTopic = sheet;
      }
      // 构造 MindElixir 需要的外层对象
      ObjectNode out = objectMapper.createObjectNode();
      // 递归把 XMind topic 节点转换为 MindElixir 的 nodeData 结构
      out.set("nodeData", toMindElixirNode(rootTopic, fallbackRootTopic));
      // 返回给调用方（通常由控制器写入 test_case.mind_map_json）
      return out;
    } catch (IllegalArgumentException e) {
      // 业务类异常原样抛出，交给全局异常处理器转成 400
      throw e;
    } catch (Exception e) {
      // 其余异常统一包装成参数错误语义，避免向前端泄露内部栈信息
      throw new IllegalArgumentException("解析 XMind 失败：" + e.getMessage());
    }
  }

  private ObjectNode toMindElixirNode(JsonNode topicNode, String fallbackTitle) {
    // 读取 XMind 节点 id（若缺失则生成一个随机 id，保证 MindElixir 节点可唯一识别）
    String id = text(topicNode, "id");
    if (!StringUtils.hasText(id)) {
      // UUID 生成后去掉 '-'，使 id 更短更适合前端渲染/存储
      id = UUID.randomUUID().toString().replace("-", "");
    }
    // 读取 XMind 节点 title 作为 MindElixir 的 topic
    String title = text(topicNode, "title");
    if (!StringUtils.hasText(title)) {
      // 若 XMind 没有 title，则使用兜底标题（优先 fallbackTitle，否则固定“用例”）
      title = StringUtils.hasText(fallbackTitle) ? fallbackTitle : "用例";
    }
    // 构造 MindElixir 节点对象：{ id, topic, children }
    ObjectNode n = objectMapper.createObjectNode();
    // 写入节点唯一 id
    n.put("id", id);
    // 写入节点显示文本 topic
    n.put("topic", title);

    // 构造 children 数组：递归转换所有子主题
    ArrayNode children = objectMapper.createArrayNode();
    // 提取 XMind 子节点集合（兼容 attached/topics 等不同字段）
    for (JsonNode child : extractChildren(topicNode)) {
      // 递归转换子节点；子节点不再传 fallbackTitle（避免覆盖真实标题）
      children.add(toMindElixirNode(child, null));
    }
    // 写入 children（即使为空也写入，便于前端稳定渲染）
    n.set("children", children);
    // 返回当前节点
    return n;
  }

  private Iterable<JsonNode> extractChildren(JsonNode topicNode) {
    // 空节点直接返回空数组，避免后续 path/get 抛异常
    if (topicNode == null || topicNode.isMissingNode() || topicNode.isNull()) {
      // 使用 Jackson 创建空 ArrayNode 作为 Iterable 返回
      return objectMapper.createArrayNode();
    }
    // children 常见为对象：{ attached: [...] }
    JsonNode children = topicNode.path("children");
    if (children != null && children.isObject()) {
      // 兼容：XMind 常见子节点位于 children.attached
      JsonNode attached = children.path("attached");
      if (attached != null && attached.isArray()) {
        // 返回 attached 数组
        return attached;
      }
      // 兼容其它命名
      JsonNode topics = children.path("topics");
      if (topics != null && topics.isArray()) {
        // 返回 topics 数组
        return topics;
      }
    }
    // 兼容：children 直接是数组
    if (children != null && children.isArray()) {
      // 返回 children 数组
      return children;
    }
    // 兼容：直接叫 topics
    JsonNode topics = topicNode.path("topics");
    if (topics != null && topics.isArray()) {
      // 返回 topics 数组
      return topics;
    }
    // 未命中任何字段时，返回空数组
    return objectMapper.createArrayNode();
  }

  private String text(JsonNode n, String field) {
    // 空节点直接返回空串，避免 NPE
    if (n == null || n.isMissingNode() || n.isNull()) {
      return "";
    }
    // 从节点上读取指定字段
    JsonNode v = n.get(field);
    // 字段存在且非 null 时转为文本，否则返回空串
    return v != null && !v.isNull() ? v.asText("") : "";
  }

  private String readZipEntryUtf8(MultipartFile file, String entryName) {
    // try-with-resources：确保输入流/zip流/缓冲流在任何情况下都会关闭，避免资源泄露
    try (InputStream in = file.getInputStream();
        ZipInputStream zis = new ZipInputStream(in);
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // 逐个遍历 zip 里的文件条目
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        // 只处理非目录且名称匹配 entryName 的条目（如 content.json）
        if (!e.isDirectory() && entryName.equals(e.getName())) {
          // 读入条目内容（二进制），累积到内存缓冲区
          byte[] buf = new byte[4096];
          int len;
          while ((len = zis.read(buf)) > 0) {
            // 把读取到的片段写入内存输出流
            baos.write(buf, 0, len);
          }
          // 按 UTF-8 解码为字符串（XMind 的 JSON 文件通常是 UTF-8）
          return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
      }
      // zip 中没有找到目标 entryName 时返回空串，由上层决定是否报错
      return "";
    } catch (Exception ex) {
      // 任何 IO/zip 解压异常都视为“文件内容不可读/不是合法 xmind”
      throw new IllegalArgumentException("读取 XMind 内容失败：" + ex.getMessage());
    }
  }
}

