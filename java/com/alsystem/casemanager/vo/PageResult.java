package com.alsystem.casemanager.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/** 分页返回通用 VO。 */
public class PageResult<T> {

  private long total;
  private List<T> list;
  private long pageNum;
  private long pageSize;
  private long pages;
}
