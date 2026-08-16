-- V3 文件索引：搜索/统计/大屏用（文件系统仍是真源）
-- 约束：SQL 同时兼容 SQLite 与 PostgreSQL；主键类型经 Flyway placeholder ${db-id-type} 按库分支

CREATE TABLE file_index (
    id         ${db-id-type},
    path       TEXT NOT NULL UNIQUE,   -- 存储根下相对路径
    name       TEXT NOT NULL,
    ext        TEXT,
    kind       TEXT NOT NULL,          -- dir / file / image / video / note / other
    size       INTEGER,
    mtime      INTEGER,
    owner      TEXT,                   -- 最后操作者
    indexed_at INTEGER
);

CREATE INDEX idx_file_index_name ON file_index (name);
CREATE INDEX idx_file_index_kind ON file_index (kind);
