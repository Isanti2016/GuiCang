package com.guicang.nas.module.user.dto;

import java.util.List;

/** 用户分页结果。 */
public record UserPage(List<UserVO> records, long total) {}
