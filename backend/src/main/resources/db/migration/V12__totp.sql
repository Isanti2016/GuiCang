-- V12 两步验证：sys_user 增加 TOTP 密钥
ALTER TABLE sys_user ADD COLUMN totp_secret TEXT;
