package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ZonedDecimalTest {
    @Test
    void roundTripsAll20OverpunchChars() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            String p = "123" + pos.charAt(d);
            String n = "123" + neg.charAt(d);
            assertEquals(1230 + d, ZonedDecimal.decode(p));
            assertEquals(-(1230 + d), ZonedDecimal.decode(n));
            assertEquals(p, ZonedDecimal.encode(1230 + d, 4));
            assertEquals(n, ZonedDecimal.encode(-(1230 + d), 4));
        }
    }

    @Test
    void zeroSigns() {
        assertEquals("000{", ZonedDecimal.encode(0, 4));
        assertEquals("000}", ZonedDecimal.encode(0, 4, true));
        assertEquals(0, ZonedDecimal.decode("000}"));
    }

    @Test
    void wrapKeepsSignAndDropsHighDigits() {
        assertEquals(1, ZonedDecimal.wrap(100_000_000_001L, 11));
        assertEquals(-1, ZonedDecimal.wrap(-100_000_000_001L, 11));
    }

    @Test
    void rejectsBadOverpunch() {
        assertThrows(IllegalArgumentException.class, () -> ZonedDecimal.decode("123Z"));
    }
}
