package com.alsystem.casemanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("test_case")
/** 测试用例实体。 */
public class TestCase {

  /** 用例主键。 */
  @TableId(type = IdType.ASSIGN_ID)
  private Long caseId;

  /** 所属项目 ID。 */
  private Long projectId;
  /** 用例编码。 */
  private String caseCode;
  /** 用例名称。 */
  private String caseName;
  /** 用例类型。 */
  private Integer caseType;
  /** 用例等级。 */
  private Integer caseLevel;
  /** 用例状态。 */
  private Integer caseStatus;
  /** 目录路径。 */
  private String folderPath;
  /** 所属目录 ID。 */
  private Long folderId;
  /** 脑图/内容 JSON。 */
  private String mindMapJson;

  /** 逻辑删除标记。 */
  @TableLogic
  private Integer delFlag;
  /** 创建人 ID。 */
  private Long createBy;
  /** 创建时间。 */
  private LocalDateTime createTime;
  /** 更新人 ID。 */
  private Long updateBy;
  /** 更新时间。 */
  private LocalDateTime updateTime;
}
