package com.alsystem.casemanager.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
/** 用户登录请求 DTO。 */
public class LoginDTO {

  @NotBlank(message = "用户名不能为空")
  private String username;

  @NotBlank(message = "密码不能为空")
  private String password;
}
