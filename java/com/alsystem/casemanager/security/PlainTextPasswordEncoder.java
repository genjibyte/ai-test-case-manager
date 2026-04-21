package com.alsystem.casemanager.security;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 明文密码编码器：数据库存储与比对均为明文。若后续需要加密可再切换为 BCrypt 等实现。
 */
public class PlainTextPasswordEncoder implements PasswordEncoder {

  @Override
  /** 明文返回密码。 */
  public String encode(CharSequence rawPassword) {
    return rawPassword != null ? rawPassword.toString() : null;
  }

  @Override
  /** 明文比对密码。 */
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (rawPassword == null && encodedPassword == null) {
      return true;
    }
    if (rawPassword == null || encodedPassword == null) {
      return false;
    }
    return rawPassword.toString().equals(encodedPassword);
  }
}
