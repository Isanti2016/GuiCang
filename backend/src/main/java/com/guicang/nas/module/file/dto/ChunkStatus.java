package com.guicang.nas.module.file.dto;

import java.util.List;

/** 分片上传断点续传状态：已上传分片序号列表与累计字节数。 */
public record ChunkStatus(String uploadId, List<Integer> uploadedChunks, long uploadedBytes) {}
