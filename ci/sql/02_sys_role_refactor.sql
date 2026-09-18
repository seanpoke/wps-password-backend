-- ============================================================
-- 增量迁移 02：sys_role 由 (type, account) 维度改造为角色定义表 (code/name/priority/remark)
-- 适用：ddl-auto=none，手动执行；幂等可重跑
-- 前置：ci/sql/init.sql（sys_role 仅有 type/account）+ 01_create_core_tables.sql
-- ============================================================
USE doc_auth_system;

-- 1. 新增角色定义列（先放可为空，便于在已有行上追加）
ALTER TABLE sys_role ADD COLUMN code    VARCHAR(50)  NULL COMMENT '角色编码';
ALTER TABLE sys_role ADD COLUMN name    VARCHAR(100) NULL COMMENT '角色名称';
ALTER TABLE sys_role ADD COLUMN priority INT         NULL COMMENT '优先级（0 最大，数值越大越低）';
ALTER TABLE sys_role ADD COLUMN remark  VARCHAR(255) NULL COMMENT '说明';

-- 2. 插入标准角色定义（幂等）
INSERT INTO sys_role (code, name, priority, remark, create_time)
SELECT 'admin', '超级管理员', 0,  '全量可见', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'admin');

INSERT INTO sys_role (code, name, priority, remark, create_time)
SELECT 'user',  '普通用户',   10, '默认角色', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'user');

-- 3. 收尾列约束
ALTER TABLE sys_role MODIFY COLUMN code    VARCHAR(50)  NOT NULL;
ALTER TABLE sys_role MODIFY COLUMN name    VARCHAR(100) NOT NULL;
ALTER TABLE sys_role MODIFY COLUMN priority INT         NOT NULL DEFAULT 0;
ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_code (code);

-- 4. 移除旧维度列（account / type）
ALTER TABLE sys_role DROP COLUMN account;
ALTER TABLE sys_role DROP COLUMN type;
