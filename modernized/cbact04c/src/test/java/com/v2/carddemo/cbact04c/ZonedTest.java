package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void roundTrip() {
        for (String s : new String[] {"0000000447A", "0000000000{", "0000000187G", "0000000123J", "0000000009R"}) {
            assertEquals(s, Zoned.format(Zoned.parse(s, 2), 11));
        }
    }

    @Test
    void signs() {
        assertEquals(new BigDecimal("-1.21"), Zoned.parse("0000000012J", 2));
        assertEquals(new BigDecimal("1.21"), Zoned.parse("0000000012A", 2));
        assertEquals(new BigDecimal("0.00"), Zoned.parse("0000000000}", 2));
        assertEquals(new BigDecimal("1.25"), Zoned.parse("00000000125", 2));
    }

    @Test
    void negativeZeroWrittenPositive() {
        assertEquals("0000000000{", Zoned.format(new BigDecimal("-0.00"), 11));
    }

    @Test
    void highOrderDigitsDropped() {
        assertEquals("0000000022C", Zoned.format(new BigDecimal("100000000002.23"), 11));
    }
}
