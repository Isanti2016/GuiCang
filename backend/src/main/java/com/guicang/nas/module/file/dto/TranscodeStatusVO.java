package com.guicang.nas.module.file.dto;

/** 视频转码任务状态。 */
public record TranscodeStatusVO(
    /** IDLE / RUNNING / DONE / FAILED */
    String status,
    /** 0-100，未知时长时保持 0 */
    int progress,
    /** 附加信息（失败原因等） */
    String message,
    /** compat 输出相对路径（DONE 后可直接 stream 播放） */
    String outputPath) {}
