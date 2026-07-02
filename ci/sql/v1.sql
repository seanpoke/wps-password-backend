USE doc_auth_system;

-- 1. 修改 doc_config 表
ALTER TABLE doc_config
    MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 2. 修改 doc_info 表
ALTER TABLE doc_info
    MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 3. 修改 doc_share_rel 表
ALTER TABLE doc_share_rel
    MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- 为 doc_info 表新增 file_name 字段
ALTER TABLE doc_info ADD COLUMN file_name VARCHAR(255) DEFAULT NULL COMMENT '文件名' AFTER uid;

-- 移除 doc_info 表的 update_time 和 update_by 字段
ALTER TABLE doc_info DROP COLUMN update_time, DROP COLUMN update_by;

-- 添加 Redis Token 过期时间配置（如果不存在则插入）
INSERT INTO doc_config (`type`, `key`, `value`, `remark`)
SELECT 'redis-config', 'token-expire', '4320', 'Redis Token过期时间，单位分钟（默认72小时）'
WHERE NOT EXISTS (
    SELECT 1 FROM doc_config WHERE `type` = 'redis-config' AND `key` = 'token-expire'
);

-- 创建系统角色表
CREATE TABLE IF NOT EXISTS sys_role
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    type        VARCHAR(64) NOT NULL COMMENT '角色类型 (admin 超级管理员 / user 普通用户)',
    account     VARCHAR(64) NOT NULL COMMENT '账号',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_account (account)
) ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci COMMENT ='系统角色表';