package com.guicang.nas.module.user.dto;

import java.util.List;

/**
 * 角色视图。
 *
 * @param id 角色 id
 * @param code 角色编码
 * @param name 角色名
 * @param description 描述
 * @param permissionCodes 已授权权限编码
 * @param userCount 绑定用户数
 */
public record RoleVO(
    Long id,
    String code,
    String name,
    String description,
    List<String> permissionCodes,
    long userCount) {}
