package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void decodesEverySignChar() {
        String pos = "{ABCDEFGHI";
        String neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            assertEquals(new BigDecimal(d).movePointLeft(2), Zoned.decode("000" + pos.charAt(d)));
            assertEquals(new BigDecimal(-d).movePointLeft(2), Zoned.decode("000" + neg.charAt(d)));
        }
    }

    @Test
    void negativeZeroDecodesAndEncodesPositive() {
        assertEquals(0, Zoned.decode("0000}").signum());
        assertEquals("0000{", Zoned.encode(Zoned.decode("0000}"), 5));
    }

    @Test
    void encodeRoundTrips() {
        assertEquals("0000000187G", Zoned.encode(new BigDecimal("18.77"), 11));
        assertEquals("0000000187P", Zoned.encode(new BigDecimal("-18.77"), 11));
    }

    @Test
    void overflowDropsHighOrderDigits() {
        assertEquals("456G", Zoned.encode(new BigDecimal("12345.67"), 4));
        assertEquals(new BigDecimal("45.67"), Zoned.fit(new BigDecimal("12345.67"), 4));
        assertEquals(new BigDecimal("-45.67"), Zoned.fit(new BigDecimal("-12345.67"), 4));
    }
}
