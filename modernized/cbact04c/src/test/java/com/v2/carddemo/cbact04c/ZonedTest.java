package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void overpunchRoundTrip() {
        for (String s : new String[] {"0000000000{", "0000000012D", "0000000012M", "0000000010}", "0000000099I"}) {
            assertEquals(s, Zoned.format(Zoned.parse(s), 9));
        }
    }

    @Test
    void signsAndPlainDigits() {
        assertEquals(new BigDecimal("-1.23"), Zoned.parse("0000000012L"));
        assertEquals(new BigDecimal("1.25"), Zoned.parse("00000000125"));
        assertEquals(new BigDecimal("-0.10"), Zoned.parse("0000000001}"));
    }

    @Test
    void negativeZeroWrittenAsPositive() {
        assertEquals("0000000000{", Zoned.format(new BigDecimal("-0.00"), 9));
    }

    @Test
    void truncKeepsLowOrderDigits() {
        assertEquals(new BigDecimal("2345678901.23"), Zoned.trunc(new BigDecimal("12345678901.23"), 10));
        assertEquals(new BigDecimal("-0.05"), Zoned.trunc(new BigDecimal("-1000000000.05"), 9));
    }
}
