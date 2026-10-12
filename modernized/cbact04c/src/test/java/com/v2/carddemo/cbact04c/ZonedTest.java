package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void everyOverpunchRoundTrips() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            BigDecimal v = Zoned.decode("0000000001" + pos.charAt(d));
            assertEquals(new BigDecimal(10 + d).movePointLeft(2), v);
            assertEquals("0000000001" + pos.charAt(d), Zoned.encode(v, 11));
            if (d > 0) {
                BigDecimal n = Zoned.decode("0000000001" + neg.charAt(d));
                assertEquals(v.negate(), n);
                assertEquals("0000000001" + neg.charAt(d), Zoned.encode(n, 11));
            }
        }
    }

    @Test
    void zeroAndNegativeZero() {
        assertEquals(new BigDecimal("0.00"), Zoned.decode("00000000000{"));
        assertEquals(0, Zoned.decode("00000000000}").signum());
        assertEquals("00000000000{", Zoned.encode(Zoned.decode("00000000000}"), 12));
        assertEquals("00000000000{", Zoned.encode(new BigDecimal("0.00"), 12));
    }

    @Test
    void plainDigitIsPositive() {
        assertEquals(new BigDecimal("0.05"), Zoned.decode("00000000005"));
    }

    @Test
    void maxAndMin() {
        BigDecimal max = new BigDecimal("999999999.99");
        assertEquals("9999999999I", Zoned.encode(max, 11));
        assertEquals(max, Zoned.decode("9999999999I"));
        assertEquals(max.negate(), Zoned.decode("9999999999R"));
        assertEquals("9999999999R", Zoned.encode(max.negate(), 11));
    }

    @Test
    void malformedRejected() {
        assertThrows(NumberFormatException.class, () -> Zoned.decode("00000X00001{"));
        assertThrows(NumberFormatException.class, () -> Zoned.decode("0000000000 "));
        assertThrows(NumberFormatException.class, () -> Zoned.decode("00000000000Z"));
    }
}
