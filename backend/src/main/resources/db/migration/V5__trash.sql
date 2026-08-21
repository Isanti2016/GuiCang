-- V5 回收站：软删除记录（文件实际存于存储根 .guicang-trash/）
-- 约束：SQL 同时兼容 SQLite 与 PostgreSQL；主键类型经 Flyway placeholder ${db-id-type} 按库分支

CREATE TABLE trash_item (
    id            ${db-id-type},
    original_path TEXT NOT NULL,   -- 原路径（恢复目标）
    trash_path    TEXT NOT NULL,   -- 回收站内路径（.guicang-trash/...）
    username      TEXT NOT NULL,   -- 删除者
    kind          TEXT,            -- dir / image / video / note / other
    size          INTEGER,
    deleted_at    INTEGER NOT NULL
);
CREATE INDEX idx_trash_username ON trash_item (username);
