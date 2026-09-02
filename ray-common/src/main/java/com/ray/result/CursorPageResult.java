package com.ray.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 不透明排序游标分页结果。 */
@Schema(name = "CursorPageResult", description = "不透明排序游标分页结果")
public record CursorPageResult<T>(
        @Schema(description = "当前批次数据") List<T> items,
        @Schema(description = "下一页排序游标；客户端只透传", example = "1760000000000") long nextCursor,
        @Schema(description = "下一页同排序值偏移量", example = "0", minimum = "0") int nextOffset,
        @Schema(description = "是否可能还有下一页") boolean hasMore) {
    public CursorPageResult {
        items = List.copyOf(items);
    }
}
