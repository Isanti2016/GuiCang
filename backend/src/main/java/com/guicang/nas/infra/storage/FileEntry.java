package com.guicang.nas.infra.storage;

/** 目录列表项。 */
public record FileEntry(
    String name, String path, boolean dir, long size, long mtime, String kind) {}
