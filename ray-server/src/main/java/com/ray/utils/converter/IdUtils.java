package com.ray.utils.converter;

import com.ray.exception.BusinessException;

/** HTTP 字符串 ID 与后端 Long ID 的转换工具。 */
public final class IdUtils {
    private IdUtils() {}

    public static Long parse(String value, String field) {
        try {
            if (value == null || value.isBlank()) {
                throw new NumberFormatException();
            }
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw BusinessException.badRequest("INVALID_ID", field + "必须是正整数字符串");
        }
    }

    /** 解析允许 0 作为“未关联”哨兵值的字符串 ID。 */
    public static Long parseNonNegative(String value, String field) {
        try {
            if (value == null || value.isBlank()) throw new NumberFormatException();
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw BusinessException.badRequest("INVALID_ID", field + "必须是非负整数字符串");
        }
    }

    public static String format(Long value) {
        return value == null ? null : value.toString();
    }
}
