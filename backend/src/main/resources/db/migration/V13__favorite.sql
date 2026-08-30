-- V13 文件收藏：快速访问常用文件/目录
CREATE TABLE favorite (
    id         ${db-id-type},
    path       TEXT NOT NULL,
    username   TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (path, username)
);
CREATE INDEX idx_favorite_username ON favorite (username);
