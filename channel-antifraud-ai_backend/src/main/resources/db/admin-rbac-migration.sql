-- 管理员 RBAC 一次性升级脚本。
-- 仅用于已经创建过 users 表的现有数据库；全新部署直接执行 schema-config.sql 即可。
USE cfrm_agent;

ALTER TABLE users
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER'
        COMMENT '系统角色：ADMIN/USER' AFTER avatar_path;

-- 将最早注册的账号设为首位管理员，后续可在用户管理页面继续赋权。
UPDATE users
SET role = 'ADMIN'
WHERE id = (SELECT first_user.id FROM (
    SELECT MIN(id) AS id FROM users
) first_user)
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT role FROM users) current_users WHERE role = 'ADMIN'
  );

CREATE TABLE IF NOT EXISTS user_data_source_permission (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    data_source VARCHAR(16) NOT NULL COMMENT '数据源：mysql/hive',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    PRIMARY KEY (user_id, data_source),
    CONSTRAINT fk_user_data_source_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户数据源访问权限';

-- 兼容升级前行为：已有用户先获得 MySQL 权限，管理员额外获得 Hive 权限。
INSERT IGNORE INTO user_data_source_permission (user_id, data_source)
SELECT id, 'mysql' FROM users;
INSERT IGNORE INTO user_data_source_permission (user_id, data_source)
SELECT id, 'hive' FROM users WHERE role = 'ADMIN';
