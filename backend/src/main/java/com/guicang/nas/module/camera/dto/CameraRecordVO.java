package com.guicang.nas.module.camera.dto;

/** 单条监控录像记录（播放走文件流接口 /files/stream）。 */
public record CameraRecordVO(Long id, String path, String name, Long size, Long mtime) {}
