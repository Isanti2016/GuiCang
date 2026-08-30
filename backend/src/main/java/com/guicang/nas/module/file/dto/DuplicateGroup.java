package com.guicang.nas.module.file.dto;

import com.guicang.nas.infra.storage.FileEntry;
import java.util.List;

/** 重复文件分组（相同大小 + 相同 SHA-256 哈希的文件）。 */
public record DuplicateGroup(long size, String hash, List<FileEntry> files) {}
