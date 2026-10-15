package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OverpunchTest {
    @Test
    void allTwentySignCharactersDecodeAndEncode() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            BigDecimal p = Overpunch.decode("0000000012" + pos.charAt(d), 2);
            BigDecimal n = Overpunch.decode("0000000012" + neg.charAt(d), 2);
            assertEquals(new BigDecimal("1.2" + d), p);
            assertEquals(p.negate(), n);
            assertEquals("0000000012" + pos.charAt(d), Overpunch.encode(p, 11, 2));
            assertEquals("0000000012" + neg.charAt(d), Overpunch.encode(n, 11, 2));
        }
    }

    @Test
    void zeroIsAlwaysEncodedPositive() {
        assertEquals("0000000000{", Overpunch.encode(Overpunch.decode("0000000000}", 2), 11, 2));
        assertEquals("0000000000{", Overpunch.encode(new BigDecimal("-0.004"), 11, 2));
    }

    @Test
    void D6_overflowDropsHighOrderDigits() {
        assertEquals("0000000010{", Overpunch.encode(new BigDecimal("1000000001.00"), 11, 2));
        assertEquals(new BigDecimal("1.00"), Overpunch.wrap(new BigDecimal("1000000001.00"), 11, 2));
    }
}
