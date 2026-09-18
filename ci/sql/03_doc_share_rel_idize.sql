-- ============================================================
-- 增量迁移 03：doc_share_rel 由 DN 模型改造为本地 id 模型
-- 适用：ddl-auto=none，手动执行；幂等可重跑
-- 前置：ci/sql/init.sql（doc_share_rel 含 dn 列）+ 01_create_core_tables.sql（sys_user/sys_dept 已存在）
-- 说明：移除 dn 列，新增 target_id（授权目标 id）/ invalid（失效标记）。
--       全新库由 init.sql 建表后无历史 DN 数据，无需回填；
--       若由含 DN 数据的旧库演进，请先按 target_id 回填再 DROP dn（本脚本仅做结构演进）。
-- ============================================================
USE doc_auth_system;

-- 1. 新增 id 化授权列
ALTER TABLE doc_share_rel ADD COLUMN target_id BIGINT       DEFAULT NULL COMMENT '授权目标 id（type=0 部门 id / type=1 用户 id）';
ALTER TABLE doc_share_rel ADD COLUMN invalid   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0 有效 / 1 失效（历史 DN 孤儿）';

-- 2. 移除旧 DN 列（授权改由 target_id 承载）
ALTER TABLE doc_share_rel DROP COLUMN dn;
