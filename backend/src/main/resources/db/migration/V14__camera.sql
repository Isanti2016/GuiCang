-- V14 监控摄像头：名称唯一，录像归档时自动注册
CREATE TABLE camera (
    id         ${db-id-type},
    name       TEXT NOT NULL,
    location   TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    UNIQUE (name)
);
CREATE INDEX idx_camera_name ON camera (name);
