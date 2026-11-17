package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void everySignCharRoundTrips() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            BigDecimal p = Zoned.decode("0000000012" + pos.charAt(d));
            assertEquals(new BigDecimal("1.2" + d).compareTo(p), 0);
            assertEquals("0000000012" + pos.charAt(d), Zoned.encode(p, 11));
            if (d > 0) {
                BigDecimal n = Zoned.decode("0000000012" + neg.charAt(d));
                assertEquals(p.negate(), n);
                assertEquals("0000000012" + neg.charAt(d), Zoned.encode(n, 11));
            }
        }
    }

    @Test
    void negativeZeroReadsAsZero() {
        assertEquals(0, Zoned.decode("0000000000}").signum());
        assertEquals("0000000000{", Zoned.encode(Zoned.decode("0000000000}"), 11));
    }

    @Test
    void goldenBytes() {
        assertEquals("0000000145F", Zoned.encode(new BigDecimal("14.56"), 11));
        assertEquals("00000019458G", Zoned.encode(new BigDecimal("1945.87"), 12));
    }
}
