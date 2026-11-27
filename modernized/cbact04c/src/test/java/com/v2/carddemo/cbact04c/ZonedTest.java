package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void everySignCharRoundTrips() {
        String pos = "{ABCDEFGHI";
        String neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            for (String s : new String[] {"0000000001" + pos.charAt(d), "0000000001" + neg.charAt(d)}) {
                assertEquals(s, Zoned.format(Zoned.parse(s), 11), s);
            }
        }
    }

    @Test
    void parseSigns() {
        assertEquals(new BigDecimal("-1.27"), Zoned.parse("0000000012P"));
        assertEquals(new BigDecimal("12.34"), Zoned.parse("0000000123D"));
        assertEquals(0, Zoned.parse("0000000000}").signum());
    }

    @Test
    void formatDropsHighOrderDigits() {
        assertEquals("0000000000{", Zoned.format(new BigDecimal("1000000000.00"), 11));
        assertEquals("0000000000J", Zoned.format(new BigDecimal("-1000000000.01"), 11));
    }
}
