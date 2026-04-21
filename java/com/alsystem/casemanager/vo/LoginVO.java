package com.alsystem.casemanager.vo;

import lombok.Data;

@Data
/** 登录响应 VO。 */
public class LoginVO {

  private String token;
  private String refreshToken;
  private long expiresIn;
  private UserInfoVO userInfo;
}
