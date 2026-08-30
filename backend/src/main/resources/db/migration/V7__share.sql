-- V7 分享链接：文件/目录分享（可选密码、过期时间）
CREATE TABLE share (
    id         ${db-id-type},
    token      TEXT NOT NULL UNIQUE,
    path       TEXT NOT NULL,
    password   TEXT,
    expires_at INTEGER,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX idx_share_token ON share (token);
CREATE INDEX idx_share_created_by ON share (created_by);
