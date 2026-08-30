package com.guicang.nas.module.file.dto;

import jakarta.validation.constraints.NotBlank;

/** 分片合并请求。 */
public record FileChunkCompleteRequest(
    String path, @NotBlank String filename, @NotBlank String uploadId, int totalChunks) {}
