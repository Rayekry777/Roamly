package com.ray.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 标准页码分页结果。 */
@Schema(name = "PageResult", description = "标准页码分页结果")
public record PageResult<T>(
        @Schema(description = "当前页数据") List<T> items,
        @Schema(description = "当前页码", example = "1", minimum = "1") int page,
        @Schema(description = "每页条数", example = "10", minimum = "1", maximum = "100") int size,
        @Schema(description = "总记录数", example = "42", minimum = "0") long total) {
    public PageResult {
        items = List.copyOf(items);
    }
}
