package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OverpunchTest {
    @Test
    void everySignByteRoundTrips() {
        String pos = "{ABCDEFGHI";
        String neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            String p = "000000000" + d + "0".repeat(0);
            BigDecimal v = Zoned.decode("0000000000" + pos.charAt(d), 2);
            assertEquals(new BigDecimal(d).movePointLeft(2), v.setScale(2));
            assertEquals("0000000000" + pos.charAt(d), Zoned.encode(v, 11, 2));
            if (d > 0) {
                BigDecimal n = Zoned.decode("0000000000" + neg.charAt(d), 2);
                assertEquals(v.negate(), n);
                assertEquals("0000000000" + neg.charAt(d), Zoned.encode(n, 11, 2));
            }
        }
    }

    @Test
    void negativeZeroWrittenAsPositive() {
        assertEquals("0000000000{", Zoned.encode(Zoned.decode("0000000000}", 2), 11, 2));
    }

    @Test
    void plainDigitAndBadSign() {
        assertEquals(new BigDecimal("0.05"), Zoned.decode("0000000000" + "5", 2));
        assertThrows(NumberFormatException.class, () -> Zoned.decode("0000000000?", 2));
    }

    @Test
    void highOrderTruncation() {
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("1000000000.00"), 11, 2));
    }
}
