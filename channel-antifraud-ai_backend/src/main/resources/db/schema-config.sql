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

-- 用户表：登录注册功能使用。
-- avatar_path 只保存头像相对路径（如 src/asserts/xxx.png），
-- 图片文件默认存放在前端工程 src/asserts，可通过 AVATAR_DIRECTORY 修改实际存放目录。
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号（登录账号）',
    password VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码哈希，禁止存储明文',
    avatar_path VARCHAR(255) NOT NULL COMMENT '用户头像相对路径，如 src/asserts/xxx.png',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
