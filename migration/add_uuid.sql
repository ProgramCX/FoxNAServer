-- 数据库迁移脚本：为用户添加 UUID 主键

-- 1. 为 tb_users 表添加 id 字段
ALTER TABLE tb_users ADD COLUMN id VARCHAR(64) DEFAULT NULL AFTER user_name;

-- 2. 为现有用户生成 UUID
UPDATE tb_users SET id = REPLACE(UUID(), '-', '');

-- 3. 将 id 设置为主键（需要先删除旧的主键）
-- 注意：如果有外键约束，需要先删除
-- ALTER TABLE tb_users DROP PRIMARY KEY;
-- ALTER TABLE tb_users ADD PRIMARY KEY (id);

-- 4. 为 tb_permissions 表添加 owner_uuid 字段
ALTER TABLE tb_permissions ADD COLUMN owner_uuid VARCHAR(64) DEFAULT NULL AFTER owner_name;

-- 5. 根据 owner_name 更新 owner_uuid（需要关联 tb_users 表）
UPDATE tb_permissions p
JOIN tb_users u ON p.owner_name = u.user_name
SET p.owner_uuid = u.id;

-- 6. 删除 owner_name 字段（如果确定数据已正确迁移）
-- ALTER TABLE tb_permissions DROP COLUMN owner_name;

-- 7. 为 tb_resources 表添加 owner_uuid 字段
ALTER TABLE tb_resources ADD COLUMN owner_uuid VARCHAR(64) DEFAULT NULL AFTER owner_name;

-- 8. 根据 owner_name 更新 owner_uuid
UPDATE tb_resources r
JOIN tb_users u ON r.owner_name = u.user_name
SET r.owner_uuid = u.id;

-- 9. 删除 owner_name 字段（如果确定数据已正确迁移）
-- ALTER TABLE tb_resources DROP COLUMN owner_name;

-- 验证迁移结果
-- SELECT * FROM tb_users;
-- SELECT * FROM tb_permissions;
-- SELECT * FROM tb_resources;
