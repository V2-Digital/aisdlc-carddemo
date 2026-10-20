package com.v2.carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test void positiveOverpunch() {
        assertEquals(50477, Zoned.decode("0000005047G"));
        assertEquals(0, Zoned.decode("0000000000{"));
        assertEquals(1, Zoned.decode("0000000000A"));
    }

    @Test void negativeOverpunchValues() {
        assertEquals(-1, Zoned.decode("0000000000J"));
        assertEquals(-9, Zoned.decode("0000000000R"));
        assertEquals(-91900, Zoned.decode("00000009190}"));
    }

    @Test void negativeZeroDecodesToZeroAndEncodesPositive() {
        assertEquals(0, Zoned.decode("0000000000}"));
        assertEquals("0000000000{", Zoned.encode(0, 11));
    }

    @Test void plainDigitIsPositive() {
        assertEquals(12, Zoned.decode("012"));
    }

    @Test void roundTripSigns() {
        for (long v : new long[] {0, 1, -1, 99999999999L, -99999999999L, 12345, -12345}) {
            assertEquals(v, Zoned.decode(Zoned.encode(v, 11)));
        }
        assertEquals("0000000000J", Zoned.encode(-1, 11));
        assertEquals("0000009190}", Zoned.encode(-91900, 11));
    }

    @Test void highOrderTruncationKeepsSign() {
        assertEquals(99, Zoned.truncate(100000000099L, 11));
        assertEquals(-99, Zoned.truncate(-100000000099L, 11));
        assertEquals("0000000009I", Zoned.encode(100000000099L, 11));
    }

    @Test void badSignRejected() {
        assertThrows(IllegalArgumentException.class, () -> Zoned.decode("000000000Z"));
    }
}
