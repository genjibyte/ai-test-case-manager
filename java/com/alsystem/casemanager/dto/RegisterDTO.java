package com.alsystem.casemanager.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
/** 用户注册请求 DTO。 */
public class RegisterDTO {

  @NotBlank(message = "用户名不能为空")
  @Size(min = 2, max = 64, message = "用户名为2-64个字符")
  private String username;

  @NotBlank(message = "密码不能为空")
  @Size(min = 4, max = 64, message = "密码为4-64个字符")
  private String password;

  @Size(max = 32, message = "姓名最多32个字符")
  private String realName;
}
