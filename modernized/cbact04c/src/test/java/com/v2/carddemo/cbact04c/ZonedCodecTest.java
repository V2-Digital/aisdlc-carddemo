package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedCodecTest {
    @Test void allTwentySignCharsDecode() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            assertEquals(new BigDecimal("0.0" + d), Zoned.decode("000" + pos.charAt(d), 2));
            BigDecimal n = Zoned.decode("000" + neg.charAt(d), 2);
            assertEquals(new BigDecimal("0.0" + d).negate().compareTo(n), 0);
        }
    }
    @Test void plainDigitIsPositive() { assertEquals(new BigDecimal("0.05"), Zoned.decode("0005", 2)); }
    @Test void roundTripAllLastDigits() {
        for (int v = -25; v <= 25; v++) {
            BigDecimal b = BigDecimal.valueOf(v, 2);
            assertEquals(0, b.compareTo(Zoned.decode(Zoned.encode(b, 11, 2), 2)));
        }
    }
    @Test void negativeZeroEncodesAsPositiveZero() {
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.00"), 11, 2));
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.009"), 11, 2));
        assertEquals("0000000010}", Zoned.encode(new BigDecimal("-1.00"), 11, 2));
    }
    @Test void overflowTruncatesHighOrder() {
        // 10^9 + 5.00 needs 12 digits: the high-order digit is dropped
        assertEquals("0000000050{", Zoned.encode(new BigDecimal("1000000005.00"), 11, 2));
    }
}
