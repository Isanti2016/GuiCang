-- V10 站内通知：磁盘/任务异常告警
CREATE TABLE notification (
    id         ${db-id-type},
    type       TEXT NOT NULL,
    title      TEXT NOT NULL,
    content    TEXT,
    username   TEXT,
    read_flag  INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL
);
CREATE INDEX idx_notification_username ON notification (username);
