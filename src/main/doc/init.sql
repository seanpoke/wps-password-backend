CREATE DATABASE IF NOT EXISTS doc_auth_system
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

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
    create_time DATETIME     NOT NULL COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件配置表';


-- 如果表存在则先删除
DROP TABLE IF EXISTS doc_info;

CREATE TABLE IF NOT EXISTS doc_info
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    uid         VARCHAR(64) NOT NULL COMMENT '文件唯一标识',
    account     VARCHAR(64) NOT NULL COMMENT '文档所属人账号',
    name        VARCHAR(64) DEFAULT NULL COMMENT '文档所属人名称',
    create_time DATETIME    NOT NULL COMMENT '创建时间',
    create_by   VARCHAR(64) DEFAULT NULL COMMENT '创建人账号',
    update_time DATETIME COMMENT '更新时间',
    update_by   VARCHAR(64) COMMENT '更新人账号',
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
    create_time DATETIME     NOT NULL COMMENT '创建时间',
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
    platform          VARCHAR(64) COMMENT '平台 win、android',
    before_password   VARCHAR(255) COMMENT '修改前密码',
    after_password    VARCHAR(255) COMMENT '修改后密码',
    possible_password TEXT COMMENT '可能的密码集合（JSON格式）',
    create_time       DATETIME    NOT NULL COMMENT '创建时间',
    create_by         VARCHAR(64) DEFAULT NULL COMMENT '操作人账号',
    update_time       DATETIME COMMENT '更新时间',
    update_by         VARCHAR(64) COMMENT '更新人账号',
    INDEX idx_query_log (uid, path, platform, create_by)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='密码操作日志表';


-- 如果表存在则先删除
DROP TABLE IF EXISTS doc_secret_key;

CREATE TABLE IF NOT EXISTS doc_secret_key
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    uid         VARCHAR(64) NOT NULL COMMENT '文件唯一标识',
    public_key  TEXT        NOT NULL COMMENT '公钥',
    private_key TEXT        NOT NULL COMMENT '私钥',
    key_version VARCHAR(50) NOT NULL COMMENT '密钥版本',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX idx_uid_key_version (uid, key_version),
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文档密钥表';


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



INSERT INTO doc_config (`type`, `key`, `value`, `remark`, create_time)
VALUES ('sys-config', 'no-token-url', '/account/login', '登录接口', NOW()),
       ('sys-config', 'no-token-url', '/doc/auth/tree', 'ldap树形接口', NOW()),
       ('sys-config', 'no-token-url', '/config/**', '基础配置接口', NOW()),
       ('cache-config', 'expire', '360', '本地缓存过期时间，单位分钟', NOW()),
       ('ldap-config', 'url', 'ldap://10.8.5.31:389', '域名', NOW()),
       ('ldap-config', 'username', 'ligc@greenet.com.cn', '用户名', NOW()),
       ('ldap-config', 'pswword', 'Lx3515055', '密码', NOW()),
       ('ldap-config', 'baseDn', 'OU=绿色网络,dc=greenet,dc=com,dc=cn', '基础dn', NOW()),
       ('ldap-config', 'subTree', 'OU=智云无限,OU=绿色网络,dc=greenet,dc=com,dc=cn', '树型同步子节点', NOW()),
       ('ldap-config', 'subTree', 'OU=武汉绿网,OU=绿色网络,dc=greenet,dc=com,dc=cn', '树型同步子节点', NOW());

-- 插入初始配置密钥（使用示例密钥，生产环境请替换为实际生成的密钥）
INSERT INTO config_secret_key (public_key, private_key, key_version, order_num)
VALUES ('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuY2/Hz7c7gM0O8P/8VYjDasWhdW4jyS99+Xwyghe+CVFko7KPeamzaOsUffIHQz0VAA8RH9MV1BYyuZAJ7X05Q==',
        'MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQgaqjFaKCKOm3fwBe3gsC9RqU1I2PXdZFoYDoxwvY9URigCgYIKoZIzj0DAQehRANCAAS5jb8fPtzuAzQ7w//xViMNqxaF1biPJL335fDKCF74JUWSjso95qbNo6xR98gdDPRUADxEf0xXUFjK5kAntfTl',
        'default', 1);