package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void decodeAndEncode() {
        assertEquals(new BigDecimal("1945.87"), Zoned.decode("00000019458G", 2));
        assertEquals(new BigDecimal("-15.43"), Zoned.decode("0000000154L", 2));
        assertEquals("0000000187G", Zoned.encode(new BigDecimal("18.77"), 11, 2));
        assertEquals("0000000154L", Zoned.encode(new BigDecimal("-15.43"), 11, 2));
        assertEquals("0000000000{", Zoned.encode(BigDecimal.ZERO, 11, 2));
    }

    @Test
    void highOrderDigitsDropped() {
        assertEquals(new BigDecimal("2345678901.23"), Zoned.truncate(new BigDecimal("12345678901.23"), 12, 2));
        assertEquals(new BigDecimal("-1.00"), Zoned.truncate(new BigDecimal("-100000000001.00"), 11, 2));
    }

    @Test
    void nonNumericIsBadInput() {
        assertThrows(Abend.class, () -> Zoned.decode("00000ABC000", 2));
    }
}
