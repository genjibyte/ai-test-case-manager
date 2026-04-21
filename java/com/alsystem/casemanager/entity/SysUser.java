package com.alsystem.casemanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_user")
/** 系统用户实体。 */
public class SysUser {

  /** 用户主键。 */
  @TableId(type = IdType.ASSIGN_ID)
  private Long userId;

  /** 用户名。 */
  private String username;
  /** 密码。 */
  private String password;
  /** 真实姓名。 */
  private String realName;
  /** 用户状态。 */
  private Integer status;

  /** 逻辑删除标记。 */
  @TableLogic
  private Integer delFlag;
  /** 创建时间。 */
  private LocalDateTime createTime;
  /** 更新时间。 */
  private LocalDateTime updateTime;
}
