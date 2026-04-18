package com.alsystem.casemanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("test_project_user")
/** 项目与用户关系实体。 */
public class TestProjectUser {

  /** 主键。 */
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 项目 ID。 */
  private Long projectId;
  /** 用户 ID。 */
  private Long userId;
  /** 项目角色。 */
  private Integer projectRole;
  /** 创建时间。 */
  private LocalDateTime createTime;
}
