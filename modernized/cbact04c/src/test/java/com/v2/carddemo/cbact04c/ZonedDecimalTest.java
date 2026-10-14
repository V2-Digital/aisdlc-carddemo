package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedDecimalTest {
    @Test
    void roundTripAllOverpunchChars() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            BigDecimal p = ZonedDecimal.decode("0000000012" + pos.charAt(d), 2);
            assertEquals(new BigDecimal("12" + d).movePointLeft(2), p);
            if (d > 0) {
                BigDecimal n = ZonedDecimal.decode("0000000012" + neg.charAt(d), 2);
                assertEquals(p.negate(), n);
                assertEquals("0000000012" + neg.charAt(d), ZonedDecimal.encode(n, 2, 11));
            }
            assertEquals("0000000012" + pos.charAt(d), ZonedDecimal.encode(p, 2, 11));
        }
    }

    @Test
    void negativeZeroDecodesToZeroAndZeroEncodesPositive() {
        assertEquals(0, ZonedDecimal.decode("0000000000}", 2).signum());
        assertEquals("0000000000{", ZonedDecimal.encode(BigDecimal.ZERO, 2, 11));
        assertEquals("0000000000{", ZonedDecimal.encode(new BigDecimal("-0.00"), 2, 11));
    }

    @Test
    void wideValueKeepsLowOrderDigits() {
        // 11 bytes = 9 integer digits + 2 decimals: the high-order digit is dropped
        assertEquals("2345678901B", ZonedDecimal.encode(new BigDecimal("1234567890.12"), 2, 11));
        assertEquals("2345678901K", ZonedDecimal.encode(new BigDecimal("-1234567890.12"), 2, 11));
    }
}
