-- V6 扩展同步任务为自动整理任务
-- 兼容旧数据：task_type 为空或 directory 时按 index_scan 处理

-- sync_task 扩展字段
ALTER TABLE sync_task ADD COLUMN task_type      TEXT DEFAULT 'index_scan';
ALTER TABLE sync_task ADD COLUMN target_config  TEXT;
ALTER TABLE sync_task ADD COLUMN rule_type      TEXT DEFAULT 'date_month';
ALTER TABLE sync_task ADD COLUMN rule_config    TEXT DEFAULT '{}';
ALTER TABLE sync_task ADD COLUMN action         TEXT DEFAULT 'move';
ALTER TABLE sync_task ADD COLUMN conflict       TEXT DEFAULT 'rename';

-- 为旧数据统一设置默认值
UPDATE sync_task SET task_type = 'index_scan' WHERE task_type IS NULL OR task_type = '';
UPDATE sync_task SET rule_type = 'date_month' WHERE rule_type IS NULL OR rule_type = '';
UPDATE sync_task SET action = 'move' WHERE action IS NULL OR action = '';
UPDATE sync_task SET conflict = 'rename' WHERE conflict IS NULL OR conflict = '';

-- sync_history 扩展字段（skipped 记录冲突跳过数）
ALTER TABLE sync_history ADD COLUMN task_type   TEXT DEFAULT 'index_scan';
ALTER TABLE sync_history ADD COLUMN processed     INTEGER DEFAULT 0;
ALTER TABLE sync_history ADD COLUMN succeeded     INTEGER DEFAULT 0;
ALTER TABLE sync_history ADD COLUMN failed        INTEGER DEFAULT 0;
ALTER TABLE sync_history ADD COLUMN skipped       INTEGER DEFAULT 0;
ALTER TABLE sync_history ADD COLUMN details       TEXT;
