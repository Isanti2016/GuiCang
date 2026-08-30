-- V9 文件版本历史：md/txt 编辑留档
CREATE TABLE file_version (
    id         ${db-id-type},
    path       TEXT NOT NULL,
    content    TEXT NOT NULL,
    size       INTEGER,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX idx_file_version_path ON file_version (path);
