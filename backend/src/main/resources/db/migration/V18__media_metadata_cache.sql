-- V18 媒体元数据缓存（独立表，与 file_index 解耦）
-- 必要性：file_index 是"搜索/统计"用增量表，本项目某些视频未纳入索引（如 /home/wb/nas/media/ 下部分视频）
-- 专用缓存表保证：所有成功探测过的视频，无论 file_index 有没有，都能在下次秒开
-- 设计要点：单行 = 单文件；path 主键（存储根下相对路径）
CREATE TABLE media_metadata_cache (
    path          TEXT NOT NULL UNIQUE,   -- 存储根下相对路径（主键）
    container     TEXT,
    video_codec   TEXT,                   -- 空字符串表示无视频轨
    audio_codec   TEXT,                   -- 空字符串表示无音轨
    duration_sec  INTEGER,
    width         INTEGER,
    height        INTEGER,
    audio_codecs  TEXT,                   -- JSON 数组字符串（多音轨备查）
    video_codecs  TEXT,
    has_subtitle  INTEGER DEFAULT 0,
    probed_at     INTEGER NOT NULL        -- 探测时间（epoch 毫秒）；可用于 TTL 刷新
);
CREATE INDEX idx_media_metadata_probed_at ON media_metadata_cache (probed_at);
