-- V1 基础配置与审计表
-- 约束：SQL 同时兼容 SQLite 与 PostgreSQL；主键类型经 Flyway placeholder ${db-id-type} 按库分支
CREATE TABLE sys_config (
    key        TEXT PRIMARY KEY,
    value      TEXT,
    updated_at TEXT
);

CREATE TABLE audit_log (
    id         ${db-id-type},
    username   TEXT,
    action     TEXT NOT NULL,
    resource   TEXT,
    ip         TEXT,
    user_agent TEXT,
    result     TEXT,
    detail     TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX idx_audit_log_username ON audit_log (username);
