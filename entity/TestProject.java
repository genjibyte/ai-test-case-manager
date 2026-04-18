package com.alsystem.casemanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("test_project")
/** 项目实体。 */
public class TestProject {

  /** 项目主键。 */
  @TableId(type = IdType.ASSIGN_ID)
  private Long projectId;

  /** 项目名称。 */
  private String projectName;
  /** 项目编码。 */
  private String projectCode;
  /** 项目描述。 */
  private String projectDesc;
  /** 项目状态。 */
  private Integer status;

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
