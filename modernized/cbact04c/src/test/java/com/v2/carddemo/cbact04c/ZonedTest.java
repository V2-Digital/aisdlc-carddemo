package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void overpunchRoundTrip() {
        for (String s : new String[] {"0000000187G", "0000000000{", "0000000012J", "0000000099R", "0000000005E"}) {
            assertEquals(s, Zoned.encode(Zoned.decode(s), 9));
        }
        assertEquals(new BigDecimal("18.77"), Zoned.decode("0000000187G"));
        assertEquals(new BigDecimal("-0.01"), Zoned.decode("0000000000J"));
        assertEquals(new BigDecimal("1.23"), Zoned.decode("00000000123"), "plain digit is positive");
    }

    @Test
    void negativeZeroEncodesAsPositive() {
        assertEquals("0000000000{", Zoned.encode(Zoned.decode("0000000000}"), 9));
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.005"), 9));
    }

    @Test
    void moduloOverflow() {
        assertEquals(new BigDecimal("1.00"), Zoned.truncate(new BigDecimal("1000000001.00"), 9));
        assertEquals(new BigDecimal("5.00"), Zoned.truncate(new BigDecimal("10000000005.00"), 10));
        assertEquals(new BigDecimal("-5.00"), Zoned.truncate(new BigDecimal("-1000000005.00"), 9));
    }

    @Test
    void truncatesTowardZero() {
        assertEquals(new BigDecimal("24.32"), Zoned.truncate(new BigDecimal("24.329"), 9));
        assertEquals(new BigDecimal("-24.32"), Zoned.truncate(new BigDecimal("-24.329"), 9));
    }
}
