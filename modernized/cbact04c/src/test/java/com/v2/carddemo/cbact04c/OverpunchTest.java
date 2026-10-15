package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OverpunchTest {
    @Test
    void everySignedDigitRoundTrips() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d <= 9; d++) {
            String p = "0000000000" + pos.charAt(d);
            String n = "0000000000" + neg.charAt(d);
            assertEquals(new BigDecimal(d).movePointLeft(2), Zoned.decode(p, 2));
            assertEquals(0, new BigDecimal(-d).movePointLeft(2).compareTo(Zoned.decode(n, 2)));
            assertEquals(p, Zoned.encode(Zoned.decode(p, 2), 11));
            if (d > 0) assertEquals(n, Zoned.encode(Zoned.decode(n, 2), 11));
        }
    }

    @Test
    void negativeZeroDecodesToZeroAndEncodesAsPositiveZero() {
        assertEquals(0, Zoned.decode("00000000000}", 2).signum());
        assertEquals("00000000000{", Zoned.encode(Zoned.decode("00000000000}", 2), 12));
    }

    @Test
    void multiDigitValues() {
        assertEquals(new BigDecimal("2339.97"), Zoned.decode("0000023399G", 2));
        assertEquals("0000000292D", Zoned.encode(new BigDecimal("29.24"), 11));
        assertEquals("0000000187G", Zoned.encode(new BigDecimal("18.77"), 11));
    }
}
