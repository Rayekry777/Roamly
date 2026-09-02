package com.ray.utils.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ray.exception.BusinessException;
import org.junit.jupiter.api.Test;

class IdUtilsTest {

    @Test
    void preservesLongIdentifiersAsStrings() {
        long id = 9_223_372_036_854_775_000L;
        assertEquals(Long.toString(id), IdUtils.format(id));
        assertEquals(id, IdUtils.parse(Long.toString(id), "id"));
    }

    @Test
    void rejectsNonNumericIdentifier() {
        assertThrows(BusinessException.class, () -> IdUtils.parse("12x", "id"));
    }

    @Test
    void rejectsNonPositiveIdentifierButSupportsExplicitZeroSentinel() {
        assertThrows(BusinessException.class, () -> IdUtils.parse("0", "id"));
        assertEquals(0L, IdUtils.parseNonNegative("0", "shopId"));
    }
}
