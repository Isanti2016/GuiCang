package com.guicang.nas.module.log.dto;

import java.util.List;

/**
 * 日志分页结果。
 *
 * @param records 日志条目列表
 * @param total 匹配总数
 */
public record LogPage(List<LogEntry> records, long total) {}
