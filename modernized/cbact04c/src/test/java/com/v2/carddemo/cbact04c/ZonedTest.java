package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test void allTwentySignCharacters() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            assertEquals(new BigDecimal("0.0" + d), Zoned.decode("000" + pos.charAt(d)), "pos " + d);
            BigDecimal n = new BigDecimal("-0.0" + d);
            assertEquals(0, n.compareTo(Zoned.decode("000" + neg.charAt(d))), "neg " + d);
        }
    }

    @Test void negativeZeroDecodesToZero() {
        assertEquals(0, Zoned.decode("000}").signum());
    }

    @Test void decodeMultiDigit() {
        assertEquals(new BigDecimal("-9.99"), Zoned.decode("099R"));
        assertEquals(new BigDecimal("14.56"), Zoned.decode("0000000145F"));
    }

    @Test void encodeZeroAlwaysPositive() {
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.002"), 9));
        assertEquals("0000000000{", Zoned.encode(BigDecimal.ZERO, 9));
    }

    @Test void encodeNegative() {
        assertEquals("0000000015M", Zoned.encode(new BigDecimal("-1.54"), 9));
    }

    @Test void storeTruncatesTowardZeroAndDropsHighDigits() {
        assertEquals(new BigDecimal("1.25"), Zoned.store(new BigDecimal("1.255"), 9));
        assertEquals(new BigDecimal("-1.54"), Zoned.store(new BigDecimal("-1.543625"), 9));
        assertEquals(new BigDecimal("333324999.91"), Zoned.store(new BigDecimal("8333324999.91"), 9));
        assertEquals(new BigDecimal("-333324999.91"), Zoned.store(new BigDecimal("-8333324999.91"), 9));
    }
}
