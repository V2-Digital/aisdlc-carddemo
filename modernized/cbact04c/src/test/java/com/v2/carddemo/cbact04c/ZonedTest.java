package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void decodeSigns() {
        assertEquals(new BigDecimal("0.00"), Zoned.decode("000000000{"));
        assertEquals(new BigDecimal("1.45"), Zoned.decode("0000000014E"));
        assertEquals(new BigDecimal("-1.45"), Zoned.decode("0000000014N"));
        assertEquals(0, Zoned.decode("000000000}").signum());
    }

    @Test
    void encodePositiveZeroIsBrace() {
        assertEquals("000000000{", Zoned.encode(new BigDecimal("0.00"), 10));
        assertEquals("0000000014E", Zoned.encode(new BigDecimal("1.45"), 11));
        assertEquals("0000000014N", Zoned.encode(new BigDecimal("-1.45"), 11));
    }

    @Test
    void wrapDropsHighOrderDigits() {
        assertEquals(new BigDecimal("0.01"), Zoned.wrap(new BigDecimal("10000000000.01"), 11).setScale(2));
        assertEquals(new BigDecimal("-0.05"), Zoned.wrap(new BigDecimal("-10000000000.05"), 11));
    }
}
