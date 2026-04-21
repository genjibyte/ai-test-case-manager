package com.alsystem.casemanager.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/** 导入失败明细 VO。 */
public class CaseImportFailDetailVO {

  private int row;
  private String reason;
}
