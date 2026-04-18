package com.alsystem.casemanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_chat_session")
/**
 * AI 聊天会话实体。
 */
public class AiChatSession {

  /** 主键。 */
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 会话业务键（对外暴露给前端）。 */
  @TableField("session_key")
  private String sessionKey;

  /** 用户 ID。 */
  @TableField("user_id")
  private Long userId;

  /** 项目 ID。 */
  @TableField("project_id")
  private Long projectId;

  /** 会话标题。 */
  private String title;

  /** 会话消息快照 JSON。 */
  @TableField("messages_json")
  private String messagesJson;

  /** workflow 上下文 JSON。 */
  @TableField("workflow_context_json")
  private String workflowContextJson;

  /** 上游 Coze 会话 ID。 */
  @TableField("coze_conversation_id")
  private String cozeConversationId;

  /** 逻辑删除标记。 */
  @TableLogic private Integer delFlag;

  /** 创建时间。 */
  @TableField("create_time")
  private LocalDateTime createTime;

  /** 更新时间。 */
  @TableField("update_time")
  private LocalDateTime updateTime;
}
