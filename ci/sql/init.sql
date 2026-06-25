CREATE DATABASE IF NOT EXISTS doc_auth_system
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 如果用户不存在，先创建用户
CREATE USER IF NOT EXISTS 'testuser'@'%' IDENTIFIED BY 'test123456';

-- 授予用户对 doc_auth_system 数据库的所有权限
GRANT ALL PRIVILEGES ON doc_auth_system.* TO 'testuser'@'%';

-- 刷新权限使配置生效
FLUSH PRIVILEGES;

-- 验证权限
SHOW GRANTS FOR 'testuser'@'%';

use doc_auth_system;

-- 如果表存在则先删除
DROP TABLE IF EXISTS doc_config;

CREATE TABLE IF NOT EXISTS doc_config
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    `type`      VARCHAR(100) NOT NULL COMMENT '配置类型',
    `key`       VARCHAR(100) NOT NULL COMMENT '配置项',
    `value`     text         NOT NULL COMMENT '配置值',
    `remark`    VARCHAR(255) DEFAULT NULL COMMENT '说明',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件配置表';


-- 如果表存在则先删除
DROP TABLE IF EXISTS doc_info;

CREATE TABLE IF NOT EXISTS doc_info
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    uid         VARCHAR(64) NOT NULL COMMENT '文件唯一标识',
    file_name   VARCHAR(255) DEFAULT NULL COMMENT '文件名',
    account     VARCHAR(64) NOT NULL COMMENT '文档所属人账号',
    name        VARCHAR(64) DEFAULT NULL COMMENT '文档所属人名称',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    create_by   VARCHAR(64) DEFAULT NULL COMMENT '创建人账号',
    UNIQUE INDEX idx_uid (uid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件信息表';


-- 如果表存在则先删除
DROP TABLE IF EXISTS doc_share_rel;

CREATE TABLE IF NOT EXISTS doc_share_rel
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    uid         VARCHAR(64)  NOT NULL COMMENT '文件唯一标识',
    type        INT          NOT NULL COMMENT '关系类型（0 用户 / 1 部门）',
    name        VARCHAR(64)  NOT NULL COMMENT '账号 / 部门名称',
    dn          VARCHAR(255) NOT NULL COMMENT 'LDAP 完整路径',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    create_by   VARCHAR(64) DEFAULT NULL COMMENT '创建人账号',
    INDEX idx_uid (uid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件分享关系表';


-- 如果表存在则先删除
DROP TABLE IF EXISTS doc_password_log;

CREATE TABLE IF NOT EXISTS doc_password_log
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    uid               VARCHAR(64) NOT NULL COMMENT '文件唯一标识',
    path              VARCHAR(500) COMMENT '文件路径',
    file_name         VARCHAR(255) COMMENT '文件名',
    platform          VARCHAR(64) COMMENT '平台 win、android',
    before_password   VARCHAR(255) COMMENT '修改前密码',
    after_password    VARCHAR(255) COMMENT '修改后密码',
    possible_password TEXT COMMENT '可能的密码集合（JSON格式）',
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    create_by         VARCHAR(64) DEFAULT NULL COMMENT '操作人账号',
    update_time       DATETIME    DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    update_by         VARCHAR(64) COMMENT '更新人账号',
    INDEX idx_query_log (uid, path, platform, create_by),
    INDEX idx_create_by_file_name (create_by, file_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='密码操作日志表';


-- 如果表存在则先删除
DROP TABLE IF EXISTS config_secret_key;

CREATE TABLE IF NOT EXISTS config_secret_key
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    public_key  TEXT        NOT NULL COMMENT '公钥',
    private_key TEXT        NOT NULL COMMENT '私钥',
    key_version VARCHAR(50) NOT NULL COMMENT '密钥版本',
    order_num   INT         NOT NULL COMMENT '序号（唯一，越大优先级越高）',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX idx_key_version (key_version),
    UNIQUE INDEX idx_order_num (order_num)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='配置密钥表';


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


INSERT INTO doc_config (`type`, `key`, `value`, `remark`)
VALUES ('sys-config', 'no-token-url', '/account/login', '登录接口'),
       ('sys-config', 'no-token-url', '/doc/auth/tree', 'ldap树形接口'),
       ('sys-config', 'no-token-url', '/config/**', '基础配置接口'),
       ('cache-config', 'expire', '360', '本地缓存过期时间，单位分钟'),
       ('redis-config', 'token-expire', '4320', 'Redis Token过期时间，单位分钟（默认72小时）'),
       ('ldap-config', 'url', 'ldap://10.8.5.31:389', '域名'),
       ('ldap-config', 'username', 'pwd_ldap', '用户名'),
       ('ldap-config', 'pswword', 'GnSsroot2026!', '密码'),
       ('ldap-config', 'baseDn', 'OU=绿色网络,dc=greenet,dc=com,dc=cn', '基础dn'),
       ('ldap-config', 'subTree', 'OU=智云无限,OU=绿色网络,dc=greenet,dc=com,dc=cn', '树型同步子节点'),
       ('ldap-config', 'subTree', 'OU=武汉绿网,OU=绿色网络,dc=greenet,dc=com,dc=cn', '树型同步子节点');

-- 插入初始配置密钥（使用示例密钥，生产环境请替换为实际生成的密钥）
INSERT INTO config_secret_key (public_key, private_key, key_version, order_num)
VALUES ('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEeSCpnnaULA0Vr9RwrIwagBAUKLtMVn4N3IXUGJbIcNmRTrVUDf20cRq3ka4n6y4SkJhUYBN/jSVxDmTnUnw4Dw==',
        'MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg81v0cCTbS6NwLK/+Leg+21ojQZ6sZeq2vwHhVoZMvjmgCgYIKoZIzj0DAQehRANCAAR5IKmedpQsDRWv1HCsjBqAEBQou0xWfg3chdQYlshw2ZFOtVQN/bRxGreRrifrLhKQmFRgE3+NJXEOZOdSfDgP',
        'v1', 1);