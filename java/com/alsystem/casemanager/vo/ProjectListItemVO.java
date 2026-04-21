package com.alsystem.casemanager.vo;

import java.util.List;
import lombok.Data;

@Data
/** 项目列表项 VO。 */
public class ProjectListItemVO {

  private String projectId;
  private String projectName;
  private String projectCode;
  private String projectDesc;
  private List<MaintainerVO> maintainerList;
  private Integer status;
  private String createTime;
}
