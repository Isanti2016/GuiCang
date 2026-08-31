-- V17 视频元数据缓存：避免每次播放前都 ffprobe，提升体验；同时供前端做"编码不兼容"友好提示
-- 浏览器原生音轨支持白名单：aac / mp3 / opus / vorbis / flac / pcm_*（参考资料：MDN MediaCapabilities.decodingInfo）
-- 浏览器原生视频支持：h264 / vp8 / vp9 / av1 / theora
-- 文件系统是真正的真源，本表只是缓存；上游删除/移动文件不影响读取
ALTER TABLE file_index ADD COLUMN audio_codec TEXT;       -- 首选音轨编码（如 eac3 / aac / mp3 / opus）
ALTER TABLE file_index ADD COLUMN video_codec TEXT;       -- 首选视频编码（如 h264 / hevc / vp9 / av1）
ALTER TABLE file_index ADD COLUMN duration_sec INTEGER;   -- 时长（秒，取整；不可靠时为空）
