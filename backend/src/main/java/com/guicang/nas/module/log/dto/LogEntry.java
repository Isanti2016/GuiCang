package com.guicang.nas.module.log.dto;

/**
 * 日志条目（对应 logback JSON 一行）。
 *
 * @param timestamp ISO 时间戳（毫秒）
 * @param level 日志级别
 * @param logger 记录器名
 * @param thread 线程名
 * @param message 日志消息
 */
public record LogEntry(
    long timestamp, String level, String logger, String thread, String message) {}
