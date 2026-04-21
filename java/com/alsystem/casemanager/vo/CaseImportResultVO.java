package com.alsystem.casemanager.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
/** 批量导入结果 VO。 */
public class CaseImportResultVO {

  private int successCount;
  private int failCount;
  private List<CaseImportFailDetailVO> failDetails = new ArrayList<>();
}
