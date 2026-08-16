package com.guicang.nas.module.audit.dto;

/**
 * 审计记录入参。
 *
 * @param username 操作者用户名（可空）
 * @param action 审计动作
 * @param resource 审计对象
 * @param ip 来源 IP
 * @param userAgent 客户端 UA
 * @param result 结果：success / failed
 * @param detail 补充说明
 */
public record AuditLogCreateDTO(
    String username,
    String action,
    String resource,
    String ip,
    String userAgent,
    String result,
    String detail) {}
