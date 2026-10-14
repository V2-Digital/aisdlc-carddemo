package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void roundTripAllDigitsBothSigns() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            assertEquals(pos.charAt(d), Zoned.encode(new BigDecimal("0.0" + d), 3).charAt(2));
            assertEquals(new BigDecimal("0.0" + d), Zoned.decode("00" + pos.charAt(d), 2));
            if (d > 0) {
                assertEquals(neg.charAt(d), Zoned.encode(new BigDecimal("-0.0" + d), 3).charAt(2));
                assertEquals(new BigDecimal("-0.0" + d), Zoned.decode("00" + neg.charAt(d), 2));
            }
        }
    }

    @Test
    void zeroIsPositiveAndPlainDigitsArePositive() {
        assertEquals("00000000000{", Zoned.encode(new BigDecimal("0.00"), 12));
        assertEquals("0000000000{".length(), Zoned.encode(new BigDecimal("-0.00"), 11).length());
        assertEquals("{", Zoned.encode(new BigDecimal("-0.00"), 1));
        assertEquals(new BigDecimal("1.25"), Zoned.decode("125", 2));
    }

    @Test
    void highOrderDigitsDropped() {
        assertEquals(new BigDecimal("0.01"), Zoned.truncate(new BigDecimal("1000000000.01"), 11));
        assertEquals(new BigDecimal("-0.01"), Zoned.truncate(new BigDecimal("-1000000000.01"), 11));
    }
}
