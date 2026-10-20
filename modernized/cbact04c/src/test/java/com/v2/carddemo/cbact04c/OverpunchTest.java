package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OverpunchTest {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    @Test
    void roundTripsAllTwentySignChars() {
        for (int d = 0; d < 10; d++) {
            String p = "1234567890" + POS.charAt(d);
            BigDecimal expected = new BigDecimal("1234567890" + d).movePointLeft(2);
            assertEquals(expected, Overpunch.parse(p));
            assertEquals(p, Overpunch.format(expected, 11));
            String n = "1234567890" + NEG.charAt(d);
            assertEquals(expected.negate(), Overpunch.parse(n));
            // -0 on the last digit still carries a sign: only a pure zero is normalised
            assertEquals(n, Overpunch.format(expected.negate(), 11));
        }
    }

    @Test
    void negativeZeroIsWrittenAsPositiveZero() {
        assertEquals(0, Overpunch.parse("0000000000}").signum());
        assertEquals("0000000000{", Overpunch.format(Overpunch.parse("0000000000}"), 11));
    }

    @Test
    void highOrderDigitsAreTruncated_D7() {
        // 11-digit field holds 9 integer digits; the 10th integer digit is lost
        assertEquals("0000000000{", Overpunch.format(new BigDecimal("1000000000.00"), 11));
        assertEquals("9999999999I", Overpunch.format(new BigDecimal("99999999999.99"), 11));
    }

    @Test
    void fitTruncatesTowardZero() {
        assertEquals(new BigDecimal("-0.12"), Overpunch.fit(new BigDecimal("-0.129"), 9));
    }
}
