-- V15 小说阅读器：阅读进度（按用户+文件唯一）
CREATE TABLE reading_progress (
    id            ${db-id-type},
    username      TEXT NOT NULL,
    file_path     TEXT NOT NULL,
    chapter_index INTEGER NOT NULL DEFAULT 0,
    percent       INTEGER NOT NULL DEFAULT 0,
    updated_at    INTEGER NOT NULL,
    UNIQUE (username, file_path)
);
CREATE INDEX idx_reading_progress_user ON reading_progress (username, updated_at);
