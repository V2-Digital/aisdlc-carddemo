package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void roundTripsSignedDigits() {
        for (int v = -9; v <= 9; v++) {
            BigDecimal b = new BigDecimal(v).movePointLeft(2);
            assertEquals(b, Zoned.decode(Zoned.encode(b, 11, 2), 2), "value " + v);
        }
        assertEquals("00000000000{", Zoned.encode(BigDecimal.ZERO, 12, 2));
        assertEquals("0000000187G", Zoned.encode(new BigDecimal("18.77"), 11, 2));
        assertEquals("0000000187P", Zoned.encode(new BigDecimal("-18.77"), 11, 2));
        assertEquals(new BigDecimal("-0.01"), Zoned.decode("0000000000J", 2));
        assertEquals(new BigDecimal("0.00"), Zoned.decode("0000000000}", 2));
        assertEquals(new BigDecimal("1.50"), Zoned.decode("0000000150", 2), "plain digit is positive");
    }

    @Test
    void overflowDropsHighOrderDigitsWithoutThrowing() {  // D7
        assertEquals(new BigDecimal("0.05"), Zoned.fit(new BigDecimal("1000000000.05"), 11, 2));
        assertEquals("0000000000E", Zoned.encode(new BigDecimal("1000000000.05"), 11, 2));
        assertEquals(new BigDecimal("-0.05"), Zoned.fit(new BigDecimal("-1000000000.05"), 11, 2));
    }
}
