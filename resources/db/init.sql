CREATE TABLE IF NOT EXISTS sys_user (
  user_id BIGINT NOT NULL PRIMARY KEY COMMENT '用户ID',
  username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录用户名',
  password VARCHAR(128) NOT NULL COMMENT '登录密码(加密后)',
  real_name VARCHAR(32) COMMENT '真实姓名',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用 0禁用',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0未删除 1已删除',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS test_project (
  project_id BIGINT NOT NULL PRIMARY KEY COMMENT '项目ID',
  project_name VARCHAR(64) NOT NULL COMMENT '项目名称',
  project_code VARCHAR(64) NOT NULL COMMENT '项目编码',
  project_desc VARCHAR(512) COMMENT '项目描述',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用 0禁用',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0未删除 1已删除',
  create_by BIGINT NOT NULL COMMENT '创建人ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by BIGINT COMMENT '更新人ID',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_project_name (project_name),
  UNIQUE KEY uk_project_code (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试项目表';

CREATE TABLE IF NOT EXISTS test_project_user (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  project_role TINYINT NOT NULL DEFAULT 2 COMMENT '项目角色:1管理员 2成员',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_project_user (project_id, user_id),
  KEY idx_project_id (project_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员关联表';

CREATE TABLE IF NOT EXISTS test_folder (
  folder_id BIGINT NOT NULL PRIMARY KEY COMMENT '目录ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  parent_id BIGINT COMMENT '父目录ID',
  folder_name VARCHAR(255) NOT NULL COMMENT '目录名称',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0未删除 1已删除',
  create_by BIGINT NOT NULL COMMENT '创建人ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_project_parent (project_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用例目录表';

CREATE TABLE IF NOT EXISTS test_case (
  case_id BIGINT NOT NULL PRIMARY KEY COMMENT '用例ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  case_code VARCHAR(64) NOT NULL COMMENT '用例编号',
  case_name VARCHAR(255) NOT NULL COMMENT '用例名称',
  case_type TINYINT NOT NULL DEFAULT 1 COMMENT '用例类型',
  case_level TINYINT NOT NULL DEFAULT 3 COMMENT '用例等级',
  case_status TINYINT NOT NULL DEFAULT 1 COMMENT '用例状态',
  folder_path VARCHAR(512) NOT NULL DEFAULT '/' COMMENT '目录路径',
  folder_id BIGINT COMMENT '所属目录ID',
  mind_map_json LONGTEXT COMMENT '思维导图JSON',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0未删除 1已删除',
  create_by BIGINT NOT NULL COMMENT '创建人ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by BIGINT COMMENT '更新人ID',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_project_case_code (project_id, case_code),
  KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例表';

CREATE TABLE IF NOT EXISTS sys_operation_log (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '日志主键',
  operator_id BIGINT DEFAULT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人名称',
  operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  operation_type VARCHAR(32) NOT NULL COMMENT '操作类型',
  api_path VARCHAR(255) NOT NULL COMMENT '接口路径',
  http_method VARCHAR(16) NOT NULL COMMENT '请求方法',
  api_purpose VARCHAR(255) NOT NULL COMMENT '接口用途',
  project_id BIGINT DEFAULT NULL COMMENT '项目ID',
  file_name VARCHAR(255) DEFAULT NULL COMMENT '文件名',
  case_name VARCHAR(255) DEFAULT NULL COMMENT '用例名称/需求名称',
  upload_path VARCHAR(500) DEFAULT NULL COMMENT '上传路径(对象Key)',
  status VARCHAR(16) NOT NULL COMMENT '执行结果:SUCCESS/FAIL',
  error_message VARCHAR(500) DEFAULT NULL COMMENT '失败错误信息',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_operator_time (operator_id, operation_time),
  KEY idx_project_time (project_id, operation_time),
  KEY idx_api_path_time (api_path, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作日志表';

CREATE TABLE IF NOT EXISTS ai_chat_session (
  id BIGINT NOT NULL COMMENT '主键ID',
  session_key VARCHAR(36) NOT NULL COMMENT '对外会话ID(UUID)',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  title VARCHAR(512) DEFAULT '' COMMENT '会话标题(首条用户消息摘要)',
  messages_json LONGTEXT COMMENT '消息JSON数组',
  workflow_context_json MEDIUMTEXT COMMENT '工作流上下文快照',
  coze_conversation_id VARCHAR(128) DEFAULT NULL COMMENT 'Coze多轮会话ID',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0未删除 1已删除',
  create_time DATETIME NOT NULL COMMENT '创建时间',
  update_time DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_key (session_key),
  KEY idx_user_project (user_id, project_id),
  KEY idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI聊天会话表';
