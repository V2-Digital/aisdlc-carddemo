package com.v2.carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test void roundTripEverySignChar() {
        for (int d = 0; d < 10; d++) {
            assertEquals(d, Zoned.decode("000000000" + "{ABCDEFGHI".charAt(d)));
            assertEquals(-d, Zoned.decode("000000000" + "}JKLMNOPQR".charAt(d)));
            assertEquals("000000000" + "{ABCDEFGHI".charAt(d), Zoned.encode(d, 10));
            if (d > 0) assertEquals("000000000" + "}JKLMNOPQR".charAt(d), Zoned.encode(-d, 10));
        }
    }
    @Test void negativeZeroDecodesToZeroAndZeroEncodesPositive() {
        assertEquals(0, Zoned.decode("0000000000}"));
        assertEquals("0000000000{", Zoned.encode(0, 11));
        assertEquals("0000000000{", Zoned.encode(-0L, 11));
    }
    @Test void leftTruncation() {
        assertEquals(499, Zoned.truncate(1_000_000_000_499L, 12));
        assertEquals(-499, Zoned.truncate(-100_000_000_499L, 11));
        assertEquals("0000000049I", Zoned.encode(100_000_000_499L, 11));
        assertEquals("00000000049R", Zoned.encode(-1_000_000_000_499L, 12));
    }
}
