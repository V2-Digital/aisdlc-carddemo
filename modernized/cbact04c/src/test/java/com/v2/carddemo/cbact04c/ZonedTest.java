package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test void roundTrip() {
        for (long v : new long[] {0, 1, -1, 1456, -1456, 1945987, -99999999999L})
            assertEquals(v, Zoned.decode(Zoned.encode(v, 11)));
    }
    @Test void signs() {
        assertEquals("0000000145F", Zoned.encode(1456, 11));
        assertEquals("0000000145O", Zoned.encode(-1456, 11));
        assertEquals("0000000000{", Zoned.encode(0, 11));
        assertEquals(-5, Zoned.decode("0000000000N"));
        assertEquals(0, Zoned.decode("0000000000}"));
    }
}
