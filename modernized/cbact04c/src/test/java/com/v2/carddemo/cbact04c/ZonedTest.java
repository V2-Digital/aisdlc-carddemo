package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void roundTripAllTwentySignChars() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            BigDecimal p = Zoned.decode("000" + pos.charAt(d), 2);
            assertEquals(new BigDecimal(d).movePointLeft(2), p);
            assertEquals("000" + pos.charAt(d), Zoned.encode(p, 4, 2));
            BigDecimal n = Zoned.decode("000" + neg.charAt(d), 2);
            assertEquals(p.negate().compareTo(n), 0);
            if (d > 0) assertEquals("000" + neg.charAt(d), Zoned.encode(n, 4, 2));
        }
    }

    @Test
    void negativeZeroDecodesToZeroAndEncodesPositive() {
        assertEquals(0, Zoned.decode("000}", 2).signum());
        assertEquals("000{", Zoned.encode(Zoned.decode("000}", 2), 4, 2));
        assertEquals("000{", Zoned.encode(new BigDecimal("-0.001"), 4, 2));
    }

    @Test
    void plainDigitDecodesPositive() {
        assertEquals(new BigDecimal("1.23"), Zoned.decode("123", 2));
    }

    @Test
    void maxAndMin() {
        assertEquals("99999999999".substring(0, 10) + "I", Zoned.encode(new BigDecimal("999999999.99"), 11, 2));
        assertEquals("9999999999R", Zoned.encode(new BigDecimal("-999999999.99"), 11, 2));
        assertEquals(new BigDecimal("999999999.99"), Zoned.decode("9999999999I", 2));
        assertEquals(new BigDecimal("-999999999.99"), Zoned.decode("9999999999R", 2));
    }

    @Test
    void highOrderTruncationNoSizeError() {
        assertEquals(new BigDecimal("0.05"), Zoned.truncate(new BigDecimal("1000000000.05"), 9, 2));
        assertEquals(new BigDecimal("-0.05"), Zoned.truncate(new BigDecimal("-1000000000.05"), 9, 2));
    }

    @Test
    void invalidCharRejected() {
        assertThrows(IllegalArgumentException.class, () -> Zoned.decode("000X", 2));
        assertThrows(IllegalArgumentException.class, () -> Zoned.decode("0A0{", 2));
    }
}
