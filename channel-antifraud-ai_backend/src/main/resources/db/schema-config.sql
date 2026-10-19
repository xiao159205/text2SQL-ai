-- Schema 黑白名单配置表。
-- 应用不会自动执行此文件，请在首次部署前手动在业务 MySQL 库中执行。

use cfrm_agent;
CREATE TABLE IF NOT EXISTS schema_table_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    database_type VARCHAR(16) NOT NULL COMMENT '数据源类型：MYSQL/HIVE',
    table_name VARCHAR(128) NOT NULL COMMENT '表名',
    visible TINYINT NOT NULL DEFAULT 1 COMMENT '是否发送给大模型：1是，0否',
    custom_comment VARCHAR(512) NULL COMMENT '自定义表注释，主要供Hive使用',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_db_table (database_type, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Schema表级黑白名单配置';

CREATE TABLE IF NOT EXISTS schema_field_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    database_type VARCHAR(16) NOT NULL COMMENT '数据源类型：MYSQL/HIVE',
    table_name VARCHAR(128) NOT NULL COMMENT '表名',
    field_name VARCHAR(128) NOT NULL COMMENT '字段名',
    visible TINYINT NOT NULL DEFAULT 1 COMMENT '是否发送给大模型：1是，0否',
    custom_comment VARCHAR(512) NULL COMMENT '自定义字段注释，主要供Hive使用',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_db_table_field (database_type, table_name, field_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Schema字段级黑白名单配置';

-- Text2SQL System Prompt 自定义覆盖配置。
-- 每种数据库类型最多一条记录；没有记录时使用后端代码内置默认提示词。
CREATE TABLE IF NOT EXISTS text2sql_prompt_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    database_type VARCHAR(16) NOT NULL COMMENT '数据源类型：MYSQL/HIVE',
    content LONGTEXT NOT NULL COMMENT 'System Prompt模板，支持schema/format/maxRows占位符',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_database_type (database_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Text2SQL系统提示词配置';

-- 用户表：登录注册功能使用。
-- avatar_path 只保存头像相对路径（如 src/asserts/xxx.png），
-- 图片文件默认存放在前端工程 src/asserts，可通过 AVATAR_DIRECTORY 修改实际存放目录。
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号（登录账号）',
    password VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码哈希，禁止存储明文',
    avatar_path VARCHAR(255) NOT NULL COMMENT '用户头像相对路径，如 src/asserts/xxx.png',
    role VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '系统角色：ADMIN/USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 登录会话：服务端校验Bearer Token，并据此隔离每个用户的历史会话。
CREATE TABLE IF NOT EXISTS user_session (
    token VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '随机访问令牌',
    user_id BIGINT NOT NULL COMMENT '所属用户',
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (token),
    KEY idx_user_session_user (user_id),
    KEY idx_user_session_expires (expires_at),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录会话';

-- 数据源访问授权：普通用户只能选择管理员分配的数据源；管理员始终拥有全部数据源。
CREATE TABLE IF NOT EXISTS user_data_source_permission (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    data_source VARCHAR(16) NOT NULL COMMENT '数据源：mysql/hive',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    PRIMARY KEY (user_id, data_source),
    CONSTRAINT fk_user_data_source_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户数据源访问权限';

-- 对话会话：一个用户可以创建多个独立上下文。
CREATE TABLE IF NOT EXISTS chat_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话主键',
    user_id BIGINT NOT NULL COMMENT '所属用户',
    title VARCHAR(200) NOT NULL COMMENT '会话标题，默认取首个问题',
    database_type VARCHAR(16) NOT NULL COMMENT '会话数据源：mysql/hive',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '最后对话时间',
    PRIMARY KEY (id),
    KEY idx_chat_conversation_user_updated (user_id, updated_at),
    CONSTRAINT fk_chat_conversation_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI历史会话';

-- 对话消息：助手消息同时保存响应、SQL和AST，用于恢复页面及多轮上下文。
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息主键',
    conversation_id BIGINT NOT NULL COMMENT '所属会话',
    role VARCHAR(16) NOT NULL COMMENT 'USER/ASSISTANT',
    content TEXT NOT NULL COMMENT '用户问题或助手摘要',
    sql_text LONGTEXT NULL COMMENT '助手执行的SQL',
    query_plan_json LONGTEXT NULL COMMENT '结构化查询AST JSON',
    response_json LONGTEXT NULL COMMENT '用于恢复前端的完整ChatResponse JSON',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_chat_message_conversation (conversation_id, id),
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES chat_conversation (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话消息';
