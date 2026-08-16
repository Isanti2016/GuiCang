package com.guicang.nas.module.setup.dto;

/**
 * 初始化状态。
 *
 * @param initialized 是否已完成初始化
 * @param storageRoot 存储根路径
 */
public record SetupStatusResponse(boolean initialized, String storageRoot) {}
