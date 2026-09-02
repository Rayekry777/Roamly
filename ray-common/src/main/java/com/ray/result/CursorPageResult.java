package com.ray.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 时间游标分页结果。 */
@Schema(name = "CursorPageResult", description = "时间游标分页结果")
public record CursorPageResult<T>(
        @Schema(description = "当前批次数据") List<T> items,
        @Schema(description = "下一页时间游标", example = "1760000000000") long nextCursor,
        @Schema(description = "下一页同时间戳偏移量", example = "0", minimum = "0") int nextOffset,
        @Schema(description = "是否可能还有下一页") boolean hasMore) {
    public CursorPageResult {
        items = List.copyOf(items);
    }
}
