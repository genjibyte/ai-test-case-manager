package com.alsystem.casemanager.vo;

import java.util.List;
import lombok.Data;

@Data
/** 登录用户信息 VO。 */
public class UserInfoVO {

  private String userId;
  private String username;
  private String realName;
  private List<String> roles;
  private List<String> permissions;
}
