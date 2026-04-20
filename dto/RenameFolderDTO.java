package com.alsystem.casemanager.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
/** 重命名文件夹请求 DTO。 */
public class RenameFolderDTO {

  @NotBlank(message = "名称不能为空")
  private String folderName;
}
