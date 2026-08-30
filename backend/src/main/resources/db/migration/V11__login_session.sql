-- V11 登录会话：会话列表 + 踢下线黑名单
CREATE TABLE login_session (
    id             ${db-id-type},
    token_hash     TEXT NOT NULL UNIQUE,
    username       TEXT NOT NULL,
    ip             TEXT,
    user_agent     TEXT,
    created_at     INTEGER NOT NULL,
    last_active_at INTEGER,
    revoked        INTEGER DEFAULT 0
);
CREATE INDEX idx_login_session_username ON login_session (username);
