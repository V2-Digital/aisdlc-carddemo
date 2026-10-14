package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test void roundTripAllLastDigits() {
        for (String s : new String[] {"0000000000{", "0000000001A", "0000000009I", "0000000001J", "0000000009R"}) {
            assertEquals(s, Zoned.encode(Zoned.decode(s, 2), 11), s);
        }
    }

    @Test void negativeZeroEncodesAsPositive() {
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.00"), 11));
        assertEquals("0000000000{", Zoned.encode(Zoned.decode("0000000000}", 2), 11));
    }

    @Test void signs() {
        assertEquals(new BigDecimal("-0.12"), Zoned.decode("0000000001K", 2));
        assertEquals(new BigDecimal("1.50"), Zoned.decode("000000015{", 2).setScale(2));
    }

    @Test void highOrderDigitsDropped() {
        assertEquals("0000000000A", Zoned.encode(new BigDecimal("1000000000.01"), 11));
        assertEquals(new BigDecimal("1.00"), Zoned.truncInt(new BigDecimal("1000000001.00"), 9));
        assertEquals(new BigDecimal("-1.00"), Zoned.truncInt(new BigDecimal("-1000000001.00"), 9));
    }

    @Test void garbageRejected() {
        assertThrows(IllegalArgumentException.class, () -> Zoned.decode("00000000 0{", 2));
    }
}
