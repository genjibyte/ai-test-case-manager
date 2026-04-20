package com.alsystem.casemanager.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
/** 创建文件夹请求 DTO。 */
public class CreateFolderDTO {

  /** 字符串形式，避免前端 Number 造成雪花 ID 精度丢失 */
  @NotBlank(message = "项目ID不能为空")
  private String projectId;

  /** 父文件夹 ID，根目录不传或空字符串 */
  private String parentId;

  @NotBlank(message = "文件夹名称不能为空")
  private String folderName;
}
