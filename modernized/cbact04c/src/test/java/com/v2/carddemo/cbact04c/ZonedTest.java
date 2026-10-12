package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test void positiveDigits() {
        String s = "{ABCDEFGHI";
        for (int i = 0; i < 10; i++) {
            assertEquals(new BigDecimal("0.0" + i).movePointRight(0).setScale(2),
                    Zoned.decode("000000000" + "0" + s.charAt(i)).setScale(2).multiply(BigDecimal.ONE).setScale(2));
            assertEquals("0000000000" + s.charAt(i), Zoned.encode(new BigDecimal("0.0" + i), 11));
        }
    }

    @Test void negativeDigits() {
        String s = "}JKLMNOPQR";
        for (int i = 1; i < 10; i++) {
            assertEquals(new BigDecimal("-0.0" + i), Zoned.decode("0000000000" + s.charAt(i)));
            assertEquals("0000000000" + s.charAt(i), Zoned.encode(new BigDecimal("-0.0" + i), 11));
        }
        assertEquals(0, Zoned.decode("0000000000}").signum());
    }

    @Test void zeroResultIsAlwaysPositiveBrace() {
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.00"), 11));
        assertEquals("00000000000{", Zoned.encode(BigDecimal.ZERO, 12));
    }

    @Test void plainDigitIsPositive() {
        assertEquals(new BigDecimal("1.25"), Zoned.decode("00000000125"));
    }

    @Test void nonNumericRejected() {
        assertThrows(NumberFormatException.class, () -> Zoned.decode("0000000000?"));
        assertThrows(NumberFormatException.class, () -> Zoned.decode("00000X0000{"));
    }

    @Test void widthsAndWrap() {
        assertEquals(11, Zoned.encode(new BigDecimal("1.00"), 11).length());
        assertEquals(new BigDecimal("1.24"), Zoned.wrap(new BigDecimal("10000000001.24"), 12));
    }
}
