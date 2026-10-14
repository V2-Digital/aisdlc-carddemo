package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedDecimalTest {
    @Test
    void roundTripEveryOverpunchChar() {
        String pos = "{ABCDEFGHI";
        String neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            String p = "0000000001" + pos.charAt(d);
            String n = "0000000001" + neg.charAt(d);
            BigDecimal v = new BigDecimal("1" + d).movePointLeft(2);
            assertEquals(v, Zoned.decode(p, 2));
            assertEquals(v.negate(), Zoned.decode(n, 2));
            assertEquals(p, Zoned.encode(v, 9, 2));
            assertEquals(n, Zoned.encode(v.negate(), 9, 2));
        }
    }

    @Test
    void negativeZeroEncodesAsPositive() {
        assertEquals(0, Zoned.decode("0000000000}", 2).signum());
        assertEquals("0000000000{", Zoned.encode(Zoned.decode("0000000000}", 2), 9, 2));
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.001"), 9, 2));
    }

    @Test
    void truncatesDecimalsAndHighOrderDigits() {
        assertEquals("0000000010G", Zoned.encode(new BigDecimal("1.079"), 9, 2));
        assertEquals(new BigDecimal("0.00"), Zoned.truncate(new BigDecimal("1000000000.00"), 9, 2));
        assertEquals(new BigDecimal("1.50"), Zoned.truncate(new BigDecimal("1000000001.509"), 9, 2));
        assertEquals(new BigDecimal("-1.50"), Zoned.truncate(new BigDecimal("-1000000001.509"), 9, 2));
    }
}
