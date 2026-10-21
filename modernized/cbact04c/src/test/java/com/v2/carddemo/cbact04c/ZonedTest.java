package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test void parsesOverpunch() {
        assertEquals(new BigDecimal("1945.87"), Zoned.parse("00000019458G", 2));
        assertEquals(new BigDecimal("-1.23"), Zoned.parse("0000000012L", 2));
        assertEquals(new BigDecimal("0.00"), Zoned.parse("0000000000}", 2));
        assertEquals(new BigDecimal("12.34"), Zoned.parse("00000001234", 2));
    }

    @Test void formatsOverpunchAndZeroIsPositive() {
        assertEquals("0000000187G", Zoned.format(new BigDecimal("18.77"), 11));
        assertEquals("0000000012L", Zoned.format(new BigDecimal("-1.23"), 11));
        assertEquals("0000000000{", Zoned.format(new BigDecimal("0.00"), 11));
    }

    @Test void truncatesTowardZeroAndDropsHighOrderDigits() {
        assertEquals(new BigDecimal("-1.99"), Zoned.truncate(new BigDecimal("-1.999"), 9, 2));
        assertEquals(new BigDecimal("1.00"), Zoned.truncate(new BigDecimal("1000000001.009"), 9, 2));
    }
}
