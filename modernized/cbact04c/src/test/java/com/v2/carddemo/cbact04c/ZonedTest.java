package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test void roundTripsEveryOverpunch() {
        for (char c : "{ABCDEFGHIJKLMNOPQR".toCharArray()) {
            String f = "0000000000" + c;
            assertEquals(f, Zoned.encode(Zoned.decode(f), 11), "overpunch " + c);
        }
    }
    @Test void knownValues() {
        assertEquals(new BigDecimal("1164.87"), Zoned.decode("0000011648G"));
        assertEquals(new BigDecimal("-0.05"), Zoned.decode("0000000000N"));
        assertEquals("0000000145F", Zoned.encode(new BigDecimal("14.56"), 11));
        assertEquals("0000000012N", Zoned.encode(new BigDecimal("-1.25"), 11));
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("0.00"), 11));
    }
    @Test void wrapDropsHighOrderDigits() {
        assertEquals(new BigDecimal("0.01"), Zoned.wrap(new BigDecimal("1000000000.01"), 11));
    }
    @Test void badFieldRejected() {
        assertThrows(IllegalArgumentException.class, () -> Zoned.decode("00000000 0{"));
        assertThrows(IllegalArgumentException.class, () -> Zoned.decode("0000000000Z"));
    }
}
