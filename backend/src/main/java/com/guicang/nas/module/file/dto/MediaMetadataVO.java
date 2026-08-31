package com.guicang.nas.module.file.dto;

/** 媒体探测结果。供前端播放前判断「浏览器是否支持音视频解码」。 */
public record MediaMetadataVO(
    /** 容器格式，如 matroska,webm / avi / mov,mp4,m4a,3gp,3g2,mj2 */
    String container,
    /** 首选视频编码（如 h264 / hevc / vp9） */
    String videoCodec,
    /** 首选音轨编码（如 aac / mp3 / eac3 / ac3 / opus） */
    String audioCodec,
    /** 全部音轨编码（按 ffprobe 顺序） */
    java.util.List<String> audioCodecs,
    /** 全部视频编码 */
    java.util.List<String> videoCodecs,
    /** 时长（秒，取整） */
    long durationSec,
    /** 视频宽度（无视频轨为 0） */
    int width,
    /** 视频高度 */
    int height,
    /** 是否含内嵌字幕 */
    boolean hasSubtitle,
    /** 音轨浏览器原生支持（null 表示无音轨） */
    Boolean browserAudioSupported,
    /** 视频浏览器原生支持（null 表示无视频轨） */
    Boolean browserVideoSupported) {}
