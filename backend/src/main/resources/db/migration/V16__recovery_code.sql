-- V15 两步验证恢复码：防止 TOTP 密钥丢失/验证器不可用导致无法登录
CREATE TABLE recovery_code (
    id         ${db-id-type},
    user_id    BIGINT NOT NULL,
    code_hash  TEXT NOT NULL,
    used       INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    used_at    INTEGER
);
CREATE INDEX idx_recovery_code_user ON recovery_code (user_id);
