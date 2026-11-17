package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void roundTripEverySignChar() {
        for (String s : new String[] {"0000000000{", "0000000001A", "0000000002B", "0000000003C", "0000000004D",
                "0000000005E", "0000000006F", "0000000007G", "0000000008H", "0000000009I", "0000000001J",
                "0000000002K", "0000000003L", "0000000004M", "0000000005N", "0000000006O", "0000000007P",
                "0000000008Q", "0000000009R"}) {
            assertEquals(s, Zoned.encode(Zoned.decode(s), 11), s);
        }
    }

    @Test
    void decodeValues() {
        assertEquals(new BigDecimal("14.56"), Zoned.decode("0000000145F"));
        assertEquals(new BigDecimal("-14.51"), Zoned.decode("0000000145J"));
    }

    @Test
    void negativeZeroInputEncodesAsPositiveZero() {
        assertEquals(0, Zoned.decode("0000000000}").signum());
        assertEquals("0000000000{", Zoned.encode(Zoned.decode("0000000000}"), 11));
    }

    @Test
    void highOrderDigitsDropped() {
        assertEquals(new BigDecimal("0.05"), Zoned.wrap(new BigDecimal("1000000000.05"), 9));
        assertEquals("0000000000E", Zoned.encode(new BigDecimal("1000000000.05"), 11));
        assertEquals(new BigDecimal("-0.05"), Zoned.wrap(new BigDecimal("-1000000000.05"), 9));
    }
}
