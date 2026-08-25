package com.guicang.nas.module.file.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 批量删除请求体。 */
public record FileBatchDeleteRequest(
    @NotEmpty(message = "路径列表不能为空") @Size(max = 100, message = "单次最多删除 100 项") List<String> paths,
    boolean recursive) {}
