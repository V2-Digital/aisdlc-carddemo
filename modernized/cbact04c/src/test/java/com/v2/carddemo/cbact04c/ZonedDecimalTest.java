package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedDecimalTest {
    @Test
    void roundTripAllLastDigitsBothSigns() {
        for (int i = -999; i <= 999; i++) {
            BigDecimal v = new BigDecimal(i).movePointLeft(2);
            assertEquals(0, v.compareTo(ZonedDecimal.decode(ZonedDecimal.encode(v, 9, 2), 2)), "value " + v);
        }
    }

    @Test
    void knownEncodings() {
        assertEquals("0000000145F", ZonedDecimal.encode(new BigDecimal("14.56"), 9, 2));
        assertEquals("0000000145O", ZonedDecimal.encode(new BigDecimal("-14.56"), 9, 2));
        assertEquals("0000000000{", ZonedDecimal.encode(BigDecimal.ZERO, 9, 2));
        assertEquals("0000000187G", ZonedDecimal.encode(new BigDecimal("18.77"), 9, 2));
    }

    @Test
    void negativeZeroIsNeverEncoded() {
        assertEquals("0000000000{", ZonedDecimal.encode(new BigDecimal("-0.004"), 9, 2));
    }

    @Test
    void decodesNegativeZeroAndUnsigned() {
        assertEquals(0, BigDecimal.ZERO.compareTo(ZonedDecimal.decode("0000000000}", 2)));
        assertEquals(0, new BigDecimal("1.23").compareTo(ZonedDecimal.decode("000000123", 2)));
        assertEquals(0, new BigDecimal("-1.23").compareTo(ZonedDecimal.decode("00000012L", 2)));
    }

    @Test
    void overflowDropsHighOrderDigitsKeepingSign() {
        assertEquals("0000000000A", ZonedDecimal.encode(new BigDecimal("1000000000.01"), 9, 2));
        assertEquals("0000000000J", ZonedDecimal.encode(new BigDecimal("-1000000000.01"), 9, 2));
        assertEquals("0000000000{", ZonedDecimal.encode(new BigDecimal("1000000000.00"), 9, 2));
        assertEquals(0, new BigDecimal("0.01").compareTo(ZonedDecimal.truncate(new BigDecimal("1000000000.01"), 9, 2)));
        assertEquals(0, new BigDecimal("-0.01").compareTo(ZonedDecimal.truncate(new BigDecimal("-1000000000.01"), 9, 2)));
    }
}
