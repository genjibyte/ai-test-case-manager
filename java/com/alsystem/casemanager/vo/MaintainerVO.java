package com.alsystem.casemanager.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/** 维护人信息 VO。 */
public class MaintainerVO {

  private String userId;
  private String realName;
}
