-- V4 同步任务与执行历史
-- 约束：SQL 同时兼容 SQLite 与 PostgreSQL；主键类型经 Flyway placeholder ${db-id-type} 按库分支

CREATE TABLE sync_task (
    id           ${db-id-type},
    name         TEXT NOT NULL,
    source_type  TEXT NOT NULL,        -- directory（一期）
    source_config TEXT,                -- 源目录相对路径
    cron         TEXT,                 -- Quartz cron（6 段）
    enabled      INTEGER NOT NULL DEFAULT 1,
    last_run_at  INTEGER,
    last_status  TEXT,                 -- success / failed
    created_at   TEXT NOT NULL
);

CREATE TABLE sync_history (
    id          ${db-id-type},
    task_id     INTEGER NOT NULL,
    started_at  INTEGER NOT NULL,
    finished_at INTEGER,
    status      TEXT,                  -- running / success / failed
    added       INTEGER DEFAULT 0,
    updated     INTEGER DEFAULT 0,
    deleted     INTEGER DEFAULT 0,
    error       TEXT
);
CREATE INDEX idx_sync_history_task ON sync_history (task_id);
