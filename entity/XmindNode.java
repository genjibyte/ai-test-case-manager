package com.alsystem.casemanager.entity;

import com.alsystem.casemanager.service.impl.XmindServiceImpl;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class XmindNode {
    // 节点标题
    public String title;
    // 孩子节点
    public List<XmindNode> children = new ArrayList<>();
}
