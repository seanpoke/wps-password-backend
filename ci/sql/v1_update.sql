-- ============================================================
-- v1_update.sql  ——  统一的增量迁移脚本
-- ------------------------------------------------------------
-- 起点：已执行 ci/sql/init.sql（仅含 doc_config/doc_info/doc_share_rel/
--       config_secret_key/sys_role 基础表）。本脚本将其演进到 v1 终态。
-- 取代旧的 01~08 零散增量脚本（已删除），后续所有 SQL 改动请追加到本文件。
-- 幂等：CREATE TABLE IF NOT EXISTS + 列/索引存在性检查，可重复执行。
-- 演进内容：
--   1) 创建 init.sql 未包含的核心表 sys_user / sys_dept / sys_user_role / visible_dept_rel（终态）
--   2) sys_role：由 (type/account) 维度改造为角色定义表 (code/name/priority/remark) 并初始化角色
--   3) doc_share_rel：由 DN 模型改为本地 id 模型（target_id/invalid，移除 dn）
--   4) doc_info：补充按创建时间倒序分页索引
--   5) admin 角色可见部门配置为「全部」
-- 说明：MySQL 不支持 ADD COLUMN IF NOT EXISTS，且当前服务器版本亦不支持
--       DROP COLUMN IF EXISTS，故统一用「information_schema 判断 + 动态 SQL」
--       做存在性检查，保证可重复执行不报错。
-- ============================================================
USE doc_auth_system;

/* ===================== 1. 核心表（终态建表，幂等） ===================== */
CREATE TABLE IF NOT EXISTS sys_user (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    account        VARCHAR(64)  NOT NULL COMMENT '账号（唯一）',
    name           VARCHAR(64)  DEFAULT NULL COMMENT '名称',
    password_hash  VARCHAR(255) DEFAULT NULL COMMENT 'BCrypt 密码哈希（本地账号）',
    dept_id        BIGINT       DEFAULT NULL COMMENT '所属部门 id',
    must_change_pwd INT         DEFAULT NULL COMMENT '是否强制改密',
    source         VARCHAR(32)  DEFAULT NULL COMMENT '来源（LDAP/LOCAL）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_account (account),
    KEY idx_sys_user_update_time (update_time) COMMENT '用户管理按更新时间倒序分页'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表（本地账号）';

CREATE TABLE IF NOT EXISTS sys_dept (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT       DEFAULT NULL COMMENT '父部门 id',
    name        VARCHAR(128) DEFAULT NULL COMMENT '部门名称',
    path        VARCHAR(512) DEFAULT NULL COMMENT 'DN 风格路径',
    source      VARCHAR(32)  DEFAULT NULL COMMENT '来源（LDAP/LOCAL）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_dept_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统部门表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    user_id  BIGINT NOT NULL,
    role_id  BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

CREATE TABLE IF NOT EXISTS visible_dept_rel (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    rel_type VARCHAR(16)  NOT NULL COMMENT 'USER / ROLE',
    rel_id   BIGINT       NOT NULL COMMENT '用户/角色 id',
    dept_id  BIGINT       NOT NULL COMMENT '可见部门 id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_visible_dept_rel (rel_type, rel_id, dept_id),
    KEY idx_vdr_dept (dept_id),
    KEY idx_vdr_rel (rel_type, rel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户/角色-可见部门关联表';

-- 1.0 清理历史残留列（旧库可能仍含已废弃字段；动态判断，可重复执行）
SET @exist_email := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user' AND COLUMN_NAME='email');
SET @sql_email := IF(@exist_email>0, 'ALTER TABLE sys_user DROP COLUMN email', 'SELECT 1');
PREPARE stmt_email FROM @sql_email; EXECUTE stmt_email; DEALLOCATE PREPARE stmt_email;

/* ===================== 2. sys_role 演进（type/account → 角色定义表） ===================== */
-- 2.1 新增角色定义列（若尚未存在）
SET @exist_role_col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_role' AND COLUMN_NAME='code');
SET @sql_role_col := IF(@exist_role_col=0,
    'ALTER TABLE sys_role ADD COLUMN code VARCHAR(50) NULL COMMENT "角色编码", ADD COLUMN name VARCHAR(100) NULL COMMENT "角色名称", ADD COLUMN priority INT NULL COMMENT "优先级(0最大)", ADD COLUMN remark VARCHAR(255) NULL COMMENT "说明"',
    'SELECT 1');
PREPARE stmt_role_col FROM @sql_role_col; EXECUTE stmt_role_col; DEALLOCATE PREPARE stmt_role_col;

-- 2.2 初始化业务角色（幂等）
INSERT INTO sys_role (code, name, priority, remark, create_time)
SELECT 'admin', '超级管理员', 0, '全量可见', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code='admin');
INSERT INTO sys_role (code, name, priority, remark, create_time)
SELECT 'user', '普通用户', 10, '默认角色', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code='user');
INSERT INTO sys_role (code, name, priority, remark, create_time)
SELECT 'greenet', '绿网员工', 100, '绿网员工默认角色（LDAP 同步自动授予）', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code='greenet');

-- 2.3 收尾列约束
ALTER TABLE sys_role MODIFY COLUMN code    VARCHAR(50)  NOT NULL;
ALTER TABLE sys_role MODIFY COLUMN name    VARCHAR(100) NOT NULL;
ALTER TABLE sys_role MODIFY COLUMN priority INT         NOT NULL DEFAULT 0;

SET @exist_role_uk := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_role' AND INDEX_NAME='uk_sys_role_code');
SET @sql_role_uk := IF(@exist_role_uk=0, 'ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_code (code)', 'SELECT 1');
PREPARE stmt_role_uk FROM @sql_role_uk; EXECUTE stmt_role_uk; DEALLOCATE PREPARE stmt_role_uk;

-- 2.4 移除旧维度列（account / type，若仍存在）
SET @exist_account := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_role' AND COLUMN_NAME='account');
SET @sql_account := IF(@exist_account>0, 'ALTER TABLE sys_role DROP COLUMN account', 'SELECT 1');
PREPARE stmt_account FROM @sql_account; EXECUTE stmt_account; DEALLOCATE PREPARE stmt_account;

SET @exist_type := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_role' AND COLUMN_NAME='type');
SET @sql_type := IF(@exist_type>0, 'ALTER TABLE sys_role DROP COLUMN type', 'SELECT 1');
PREPARE stmt_type FROM @sql_type; EXECUTE stmt_type; DEALLOCATE PREPARE stmt_type;

/* ===================== 3. doc_share_rel 演进（DN → 本地 id） ===================== */
SET @exist_tid := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='doc_share_rel' AND COLUMN_NAME='target_id');
SET @sql_tid := IF(@exist_tid=0, 'ALTER TABLE doc_share_rel ADD COLUMN target_id BIGINT DEFAULT NULL COMMENT "授权目标 id（type=0 部门 / type=1 用户）"', 'SELECT 1');
PREPARE stmt_tid FROM @sql_tid; EXECUTE stmt_tid; DEALLOCATE PREPARE stmt_tid;

SET @exist_invalid := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='doc_share_rel' AND COLUMN_NAME='invalid');
SET @sql_invalid := IF(@exist_invalid=0, 'ALTER TABLE doc_share_rel ADD COLUMN invalid TINYINT(1) NOT NULL DEFAULT 0 COMMENT "0 有效 / 1 失效"', 'SELECT 1');
PREPARE stmt_invalid FROM @sql_invalid; EXECUTE stmt_invalid; DEALLOCATE PREPARE stmt_invalid;

-- 3.3 移除旧 DN 列（若存在）
SET @exist_dn := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='doc_share_rel' AND COLUMN_NAME='dn');
SET @sql_dn := IF(@exist_dn>0, 'ALTER TABLE doc_share_rel DROP COLUMN dn', 'SELECT 1');
PREPARE stmt_dn FROM @sql_dn; EXECUTE stmt_dn; DEALLOCATE PREPARE stmt_dn;

/* ===================== 4. doc_info 按创建时间倒序分页索引 ===================== */
SET @exist_doc_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='doc_info' AND INDEX_NAME='idx_doc_info_create_time');
SET @sql_doc_idx := IF(@exist_doc_idx=0, 'ALTER TABLE doc_info ADD KEY idx_doc_info_create_time (create_time)', 'SELECT 1');
PREPARE stmt_doc_idx FROM @sql_doc_idx; EXECUTE stmt_doc_idx; DEALLOCATE PREPARE stmt_doc_idx;

/* ===================== 6. doc_info.uid 唯一约束（防并发首访重复建文档） ===================== */
SET @exist_uid := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='doc_info' AND INDEX_NAME='uk_doc_info_uid');
SET @sql_uid := IF(@exist_uid=0, 'ALTER TABLE doc_info ADD UNIQUE KEY uk_doc_info_uid (uid)', 'SELECT 1');
PREPARE stmt_uid FROM @sql_uid; EXECUTE stmt_uid; DEALLOCATE PREPARE stmt_uid;

/* ===================== 5. admin 角色可见部门 = 全部 ===================== */
SET @admin_id = (SELECT id FROM sys_role WHERE code='admin');
DELETE FROM visible_dept_rel
WHERE rel_type='ROLE' AND rel_id=@admin_id AND dept_id<>0;
INSERT INTO visible_dept_rel (rel_type, rel_id, dept_id)
SELECT 'ROLE', @admin_id, 0
WHERE @admin_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM visible_dept_rel WHERE rel_type='ROLE' AND rel_id=@admin_id AND dept_id=0);

/* ===================== 6. 历史数据校正（存量 LDAP 用户，由脚本一次性完成） =====================
   代码不再处理历史数据，以下校正针对已存在的 LDAP 用户，可重复执行。
   6.1 仅保留 greenet 角色：删除 LDAP 用户多余的 user 角色（若有）
   6.2 确保 LDAP 用户拥有 greenet 角色（历史缺失则补）
   6.3 LDAP 用户 password_hash 置空（LDAP 不本地存密码） */
-- 6.0 允许 password_hash 为空（LDAP 用户不本地存密码；本地用户仍必须有值）
ALTER TABLE sys_user MODIFY COLUMN password_hash VARCHAR(255) NULL DEFAULT NULL COMMENT 'BCrypt 密码哈希（本地账号；LDAP 用户留空）';

SET @user_role_id    := (SELECT id FROM sys_role WHERE code='user');
SET @greenet_role_id := (SELECT id FROM sys_role WHERE code='greenet');

-- 6.1 删除 LDAP 用户多余的 user 角色
DELETE sur FROM sys_user_role sur
JOIN sys_user u ON u.id = sur.user_id
WHERE u.source = 'LDAP'
  AND @user_role_id IS NOT NULL
  AND sur.role_id = @user_role_id;

-- 6.2 确保 LDAP 用户拥有 greenet 角色（缺失则补）
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, @greenet_role_id
FROM sys_user u
WHERE u.source = 'LDAP'
  AND @greenet_role_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_user_role sur WHERE sur.user_id = u.id AND sur.role_id = @greenet_role_id);

-- 6.3 LDAP 用户 password_hash 置空（若非 NULL）
UPDATE sys_user SET password_hash = NULL WHERE source = 'LDAP' AND password_hash IS NOT NULL;

/* ===================== 7. LDAP 定时全量同步配置种子（幂等） =====================
   7.1 syncTimes：定时触发时刻（逗号分隔，如 10:00,20:00），页面用时间选择器编辑，不暴露 cron。
   7.2 syncEnabled：定时同步总开关（true/false，默认 true）。 */
INSERT INTO doc_config (type, `key`, value)
SELECT 'ldap-config', 'syncTimes', '10:00,20:00'
WHERE NOT EXISTS (SELECT 1 FROM doc_config WHERE type = 'ldap-config' AND `key` = 'syncTimes');

INSERT INTO doc_config (type, `key`, value)
SELECT 'ldap-config', 'syncEnabled', 'true'
WHERE NOT EXISTS (SELECT 1 FROM doc_config WHERE type = 'ldap-config' AND `key` = 'syncEnabled');
