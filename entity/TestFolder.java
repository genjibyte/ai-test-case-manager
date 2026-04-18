package com.alsystem.casemanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("test_folder")
/** 文件夹实体。 */
public class TestFolder {

  /** 文件夹主键。 */
  @TableId(type = IdType.ASSIGN_ID)
  private Long folderId;

  /** 所属项目 ID。 */
  private Long projectId;
  /** 父目录 ID。 */
  private Long parentId;
  /** 文件夹名称。 */
  private String folderName;

  /** 逻辑删除标记。 */
  @TableLogic
  private Integer delFlag;
  /** 创建人 ID。 */
  private Long createBy;
  /** 创建时间。 */
  private LocalDateTime createTime;
  /** 更新时间。 */
  private LocalDateTime updateTime;
}
