package com.guicang.nas.module.camera.dto;

/** 摄像头视图对象（含录像统计）。 */
public record CameraVO(
    Long id, String name, String location, Long totalRecords, Long lastRecordAt) {}
