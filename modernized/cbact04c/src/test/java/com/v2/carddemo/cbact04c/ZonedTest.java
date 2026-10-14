package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void roundTripAllOverpunch() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            String p = "0000000000" + pos.charAt(d), n = "0000000000" + neg.charAt(d);
            assertEquals(BigDecimal.valueOf(d, 2), Zoned.decode(p, 2));
            assertEquals(BigDecimal.valueOf(-d, 2), Zoned.decode(n, 2));
            assertEquals(p, Zoned.encode(Zoned.decode(p, 2), 2, 11));
            if (d > 0) assertEquals(n, Zoned.encode(Zoned.decode(n, 2), 2, 11));
        }
    }

    @Test
    void negativeZeroDecodesToZeroAndEncodesAsPositive() {
        BigDecimal z = Zoned.decode("00000000000}", 2);
        assertEquals(0, z.signum());
        assertEquals("00000000000{", Zoned.encode(z, 2, 12));
    }

    @Test
    void fitDropsHighOrderDigitsKeepingSign() {
        assertEquals(new BigDecimal("1.00"), Zoned.fit(new BigDecimal("1000000001.00"), 9));
        assertEquals(new BigDecimal("-1.00"), Zoned.fit(new BigDecimal("-1000000001.00"), 9));
    }
}
