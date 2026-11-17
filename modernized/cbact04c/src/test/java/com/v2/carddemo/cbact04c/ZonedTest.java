package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void parseAndFormatRoundTripAllSigns() {
        for (String s : new String[] {"0000000000{", "0000000145F", "0000000187G", "0000000145O", "0000000000J",
                "0000000000}", "0000000019I", "0000000019R"}) {
            BigDecimal v = Zoned.parse(s);
            boolean negZero = s.endsWith("}");
            assertEquals(s, negZero ? Zoned.format(v, 11, true) : Zoned.format(v, 11));
        }
    }

    @Test
    void parseValues() {
        assertEquals(new BigDecimal("14.56"), Zoned.parse("0000000145F"));
        assertEquals(new BigDecimal("-14.56"), Zoned.parse("0000000145O"));
        assertEquals(new BigDecimal("0.00"), Zoned.parse("0000000000}"));
    }

    @Test
    void formatDropsHighOrderDigits() {
        assertEquals("0000000050{", Zoned.format(new BigDecimal("1000000005.00"), 11));
    }

    @Test
    void badSignRejected() {
        assertThrows(IllegalArgumentException.class, () -> Zoned.parse("0000000001x"));
    }
}
