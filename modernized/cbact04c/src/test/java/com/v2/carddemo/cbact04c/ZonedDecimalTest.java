package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedDecimalTest {
    @Test
    void roundTrip() {
        assertEquals(new BigDecimal("18.77"), ZonedDecimal.parse("0000000187G", 2));
        assertEquals("0000000187G", ZonedDecimal.format(new BigDecimal("18.77"), 9, 2));
        assertEquals("0000000187P", ZonedDecimal.format(new BigDecimal("-18.77"), 9, 2));
    }

    @Test
    void negativeZeroIsWrittenPositive() {
        assertEquals("0000000000{", ZonedDecimal.format(new BigDecimal("-0.00"), 9, 2));
    }

    @Test
    void overflowKeepsLowOrderDigits() {
        assertEquals("0000000000{", ZonedDecimal.format(new BigDecimal("1000000000.00"), 9, 2));
    }
}
