package com.alsystem.casemanager.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/** 用户简要信息 VO。 */
public class UserSimpleVO {

  private String userId;
  private String username;
  private String realName;
}
