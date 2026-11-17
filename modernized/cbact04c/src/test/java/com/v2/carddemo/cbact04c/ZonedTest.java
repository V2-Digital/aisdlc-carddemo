package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test void overpunchRoundTrip() {
        for (String s : new String[] {"0000000145F", "0000000145{", "0000000001K", "0000000000{"}) {
            assertEquals(s, Zoned.encode(Zoned.decode(s, 2), 11));
        }
        assertEquals(new BigDecimal("-0.12"), Zoned.decode("0000000001K", 2));
        assertEquals(new BigDecimal("14.56"), Zoned.decode("0000000145F", 2));
    }

    @Test void negativeZeroDecodesToZeroAndEncodesAsPositive() {
        assertEquals(0, Zoned.decode("0000000000}", 2).signum());
        assertEquals("0000000000{", Zoned.encode(Zoned.decode("0000000000}", 2), 11));
    }

    @Test void plainDigitIsPositive() {
        assertEquals(new BigDecimal("0.15"), Zoned.decode("00000000015", 2));
    }

    @Test void negativeTruncatesTowardZero() {
        // -10.00 * 15.00 / 1200 = -0.125 -> -0.12
        BigDecimal v = Zoned.truncDivide(new BigDecimal("-10.00").multiply(new BigDecimal("15.00")), BigDecimal.valueOf(1200));
        assertEquals(new BigDecimal("-0.12"), v);
        assertEquals("0000000001K", Zoned.encode(v, 11));
        BigDecimal tiny = Zoned.truncDivide(new BigDecimal("-0.01").multiply(new BigDecimal("1.00")), BigDecimal.valueOf(1200));
        assertEquals("0000000000{", Zoned.encode(tiny, 11));
    }

    @Test void overflowDropsHighOrderDigits_D4() {
        assertEquals(new BigDecimal("0.01"), Zoned.fit(new BigDecimal("1000000000.01"), 9));
        assertEquals(new BigDecimal("-0.01"), Zoned.fit(new BigDecimal("-1000000000.01"), 9));
        
        assertEquals("3456789012C", Zoned.encode(new BigDecimal("12345678901.23"), 11));
    }
}
