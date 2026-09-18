-- ============================================================
-- 增量迁移 01：补充 init.sql 基础 schema 中缺失的核心表
-- 适用：ddl-auto=none，手动在已执行 init.sql 的库上执行
-- 前置：ci/sql/init.sql（已建 doc_config/doc_info/doc_share_rel/config_secret_key/sys_role）
-- 说明：本脚本补齐 方案C 所需的 sys_user / sys_dept / sys_user_role / visible_dept_rel
-- ============================================================
USE doc_auth_system;

-- 系统用户表（本地账号 + BCrypt 密码；LDAP 用户不落此表，仅同步时建关联）
CREATE TABLE IF NOT EXISTS sys_user (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    account        VARCHAR(64)  NOT NULL COMMENT '账号（唯一）',
    name           VARCHAR(64)  DEFAULT NULL COMMENT '名称',
    password_hash  VARCHAR(255) DEFAULT NULL COMMENT 'BCrypt 密码哈希（本地账号）',
    dept_id        BIGINT       DEFAULT NULL COMMENT '所属部门 id',
    status         INT          DEFAULT NULL COMMENT '状态',
    must_change_pwd INT         DEFAULT NULL COMMENT '是否强制改密',
    source         VARCHAR(32)  DEFAULT NULL COMMENT '来源（LDAP/LOCAL）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表（本地账号）';

-- 系统部门表（支持外部三方多级部门；path 采用 DN 风格）
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

-- 用户-角色关联（多对多）
CREATE TABLE IF NOT EXISTS sys_user_role (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    user_id  BIGINT NOT NULL,
    role_id  BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- 用户/角色-可见部门关联（取代旧"权限组"中间层：rel_type=USER/ROLE -> dept_id）
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
