package com.guicang.nas.module.file.dto;

import java.nio.file.Path;

/**
 * 文件流信息（下载/预览用）。
 *
 * @param path 已校验的文件绝对路径
 * @param size 文件大小（字节）
 * @param contentType MIME 类型
 * @param name 文件名
 */
public record FileStreamInfo(Path path, long size, String contentType, String name) {}
