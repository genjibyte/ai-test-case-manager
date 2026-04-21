package com.alsystem.casemanager.vo;

import java.util.List;
import lombok.Data;

@Data
/** 目录树节点 VO。 */
public class TreeNodeVO {

  private String id;
  private String label;
  private String type;
  private String parentId;
  private String path;
  private String caseId;
  private List<TreeNodeVO> children;
}
