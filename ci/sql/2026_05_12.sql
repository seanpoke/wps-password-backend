USE doc_auth_system;

-- 新增 file_name 字段
ALTER TABLE doc_password_log ADD COLUMN file_name VARCHAR(255) COMMENT '文件名' after uid;

-- 为已有数据填充 file_name（从 path 截取最后的文件名）
UPDATE doc_password_log SET file_name = SUBSTRING_INDEX(path, '/', -1) WHERE path IS NOT NULL AND path != '' AND LOCATE('/', path) > 0;
UPDATE doc_password_log SET file_name = SUBSTRING_INDEX(path, '\\', -1) WHERE path IS NOT NULL AND path != '' AND LOCATE('\\', path) > 0 AND file_name IS NULL;

-- 为 file_name 创建普通索引
CREATE INDEX idx_file_name_create_by ON doc_password_log(file_name,create_by);


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

-- 4. 修改 doc_password_log 表
ALTER TABLE doc_password_log
    MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
