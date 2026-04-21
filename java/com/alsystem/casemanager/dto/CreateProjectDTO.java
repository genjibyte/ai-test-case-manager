package com.alsystem.casemanager.dto;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import lombok.Data;

/** 创建项目入参；项目编码 {@code project_code} 由服务端生成 UUID（无横线），无需传入。 */
@Data
public class CreateProjectDTO {

  @NotBlank(message = "项目名称不能为空")
  private String projectName;

  private String projectDesc;

  /** 维护人员用户 ID 字符串列表；可为空，系统会自动将当前登录用户加入维护人员 */
  private List<String> maintainerIdList = new ArrayList<>();
}
