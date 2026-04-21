package com.alsystem.casemanager.service.impl;

import com.alsystem.casemanager.entity.XmindNode;
import com.alsystem.casemanager.service.XmindService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class XmindServiceImpl implements XmindService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] convertMdToXmind(String content) throws IOException {
        // 1. 解析 MD 构建树形结构
        XmindNode root = parseMdToTree(content);

        // 2. 将树形结构转换为 XMind 的 content.json 结构
        String contentJson = buildContentJson(root);

        // 3. 打包成 ZIP (即 .xmind 文件)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 写入 content.json
            ZipEntry entry = new ZipEntry("content.json");
            zos.putNextEntry(entry);
            zos.write(contentJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // XMind 还需要一个简单的 manifest.json (固定格式)
            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            zos.putNextEntry(manifestEntry);
            zos.write("{\"file-entries\":{\"content.json\":{},\"metadata.json\":{}}}".getBytes());
            zos.closeEntry();

            // 写入空的 metadata.json
            ZipEntry metaEntry = new ZipEntry("metadata.json");
            zos.putNextEntry(metaEntry);
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private XmindNode parseMdToTree(String  content) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(content));
        String line;
        XmindNode root = new XmindNode();
        root.setTitle("中心主题");
        Map<Integer, XmindNode> lastNodes = new HashMap<>();
        lastNodes.put(-1, root);

        boolean firstRealLine = true;

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;

            int spaceCount = 0;
            while (spaceCount < line.length() && line.charAt(spaceCount) == ' ') {
                spaceCount++;
            }
            int level = spaceCount / 2;
            String title = line.trim().substring(2); // 去掉 "- "

            XmindNode current = new XmindNode();
            current.setTitle(title);
            if (firstRealLine && level == 0) {
                root.title = title;
                lastNodes.put(0, root);
                firstRealLine = false;
            } else {
                XmindNode parent = lastNodes.getOrDefault(level - 1, root);
                parent.children.add(current);
                lastNodes.put(level, current);
                firstRealLine = false;
            }
        }
        return root;
    }

    private String buildContentJson(XmindNode root) {
        ArrayNode rootArray = mapper.createArrayNode();
        ObjectNode sheet = mapper.createObjectNode();
        sheet.put("id", UUID.randomUUID().toString());
        sheet.put("class", "sheet");
        sheet.put("title", "Sheet 1");

        sheet.set("rootTopic", convertTopicToJson(root));
        rootArray.add(sheet);
        return rootArray.toString();
    }

    private ObjectNode convertTopicToJson(XmindNode topic) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", UUID.randomUUID().toString());
        node.put("title", topic.title);

        if (!topic.children.isEmpty()) {
            ObjectNode childrenNode = mapper.createObjectNode();
            ArrayNode attached = mapper.createArrayNode();
            for (XmindNode child : topic.children) {
                attached.add(convertTopicToJson(child));
            }
            childrenNode.set("attached", attached);
            node.set("children", childrenNode);
        }
        return node;
    }
}