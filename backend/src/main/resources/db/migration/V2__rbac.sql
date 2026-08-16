-- V2 RBAC：用户/角色/权限/角色权限/用户目录授权
-- 约束：SQL 同时兼容 SQLite 与 PostgreSQL；主键类型经 Flyway placeholder ${db-id-type} 按库分支

CREATE TABLE sys_role (
    id          ${db-id-type},
    code        TEXT NOT NULL UNIQUE,  -- admin / member / guest / custom
    name        TEXT NOT NULL,
    description TEXT
);

CREATE TABLE sys_permission (
    id       ${db-id-type},
    code     TEXT NOT NULL UNIQUE,  -- 如 file.read / user.manage / audit.view
    name     TEXT NOT NULL,
    type     TEXT NOT NULL,         -- menu / api / dir
    resource TEXT                   -- 目录路径（type=dir 时）
);

CREATE TABLE sys_role_permission (
    role_id       INTEGER NOT NULL,
    permission_id INTEGER NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE sys_user (
    id           ${db-id-type},
    username     TEXT NOT NULL UNIQUE,  -- 对应系统账号
    uid          INTEGER,
    display_name TEXT,
    email        TEXT,
    enabled      INTEGER NOT NULL DEFAULT 1,
    role_id      INTEGER NOT NULL,      -- 主角色
    home_path    TEXT,
    quota_bytes  INTEGER,
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL
);
CREATE INDEX idx_sys_user_role ON sys_user (role_id);

CREATE TABLE sys_user_dir (  -- 用户对目录的附加授权（超出主角色）
    id       ${db-id-type},
    username TEXT NOT NULL,
    path     TEXT NOT NULL,
    perm     TEXT NOT NULL,  -- read / write / manage
    UNIQUE (username, path, perm)
);
CREATE INDEX idx_sys_user_dir_username ON sys_user_dir (username);

-- 内置角色种子
INSERT INTO sys_role (id, code, name, description) VALUES
    (1, 'admin',  '管理员', '全部权限'),
    (2, 'member', '成员',   '个人目录与指定共享'),
    (3, 'guest',  '访客',   '只读共享');

-- 内置权限点种子（功能级 + 菜单级；目录级由 sys_user_dir 动态授予）
INSERT INTO sys_permission (id, code, name, type) VALUES
    (1,  'user.manage',    '用户管理', 'menu'),
    (2,  'role.manage',    '角色管理', 'menu'),
    (3,  'audit.view',     '审计查看', 'menu'),
    (4,  'sync.manage',    '同步管理', 'menu'),
    (5,  'file.read',      '文件读取', 'api'),
    (6,  'file.write',     '文件写入', 'api'),
    (7,  'file.delete',    '文件删除', 'api'),
    (8,  'monitor.view',   '监控查看', 'menu'),
    (9,  'dashboard.view', '大屏查看', 'menu'),
    (10, 'settings.manage','系统配置', 'menu');

-- admin：全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT 1, id FROM sys_permission;
-- member：文件读写 + 大屏
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (2, 5), (2, 6), (2, 9);
-- guest：文件只读 + 大屏
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (3, 5), (3, 9);
