package com.guicang.nas.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 角色创建/更新请求。
 *
 * @param code 角色编码（admin/member/guest 为内置，新增须自定义）
 * @param name 角色名
 * @param description 描述
 * @param permissionIds 授权权限点 id 列表
 */
public record RoleUpsertRequest(
    @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = "^[a-z][a-z0-9_]{0,31}$", message = "角色编码须为小写字母/数字/下划线，且以字母开头，最长 32 位")
        String code,
    @NotBlank(message = "角色名不能为空") @Size(max = 50, message = "角色名最长 50 字符") String name,
    @Size(max = 200, message = "描述最长 200 字符") String description,
    List<Long> permissionIds) {}
