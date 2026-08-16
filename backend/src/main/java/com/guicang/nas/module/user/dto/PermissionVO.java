package com.guicang.nas.module.user.dto;

/**
 * 权限点视图。
 *
 * @param id 权限 id
 * @param code 权限编码
 * @param name 权限名
 * @param type menu / api / dir
 * @param resource 目录路径（type=dir 时）
 */
public record PermissionVO(Long id, String code, String name, String type, String resource) {}
