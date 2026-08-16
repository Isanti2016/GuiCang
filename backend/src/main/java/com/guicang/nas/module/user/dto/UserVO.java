package com.guicang.nas.module.user.dto;

/**
 * 用户视图（含角色信息）。
 *
 * @param id 用户 id
 * @param username 用户名
 * @param uid 系统 uid
 * @param displayName 显示名
 * @param email 邮箱
 * @param enabled 是否启用
 * @param roleId 角色 id
 * @param roleCode 角色编码
 * @param roleName 角色名
 * @param homePath 个人目录
 * @param quotaBytes 配额
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record UserVO(
    Long id,
    String username,
    Long uid,
    String displayName,
    String email,
    Boolean enabled,
    Long roleId,
    String roleCode,
    String roleName,
    String homePath,
    Long quotaBytes,
    String createdAt,
    String updatedAt) {}
