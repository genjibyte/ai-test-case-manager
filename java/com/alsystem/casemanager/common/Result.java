package com.alsystem.casemanager.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 通用接口返回体。
 */
public class Result<T> {

  private int code;
  private String msg;
  private T data;

  /** 成功返回（默认消息）。 */
  public static <T> Result<T> ok(T data) {
    return new Result<>(200, "操作成功", data);
  }

  /** 成功返回（自定义消息）。 */
  public static <T> Result<T> ok(String msg, T data) {
    return new Result<>(200, msg, data);
  }

  /** 失败返回。 */
  public static <T> Result<T> fail(int code, String msg) {
    return new Result<>(code, msg, null);
  }
}
