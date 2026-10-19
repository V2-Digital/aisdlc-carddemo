package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InterestTest {
    private static BigDecimal m(String bal, String rate) {
        return Cbact04c.monthlyInterest(new BigDecimal(bal), new BigDecimal(rate));
    }

    @Test
    void goldenAccount1() {
        assertEquals(new BigDecimal("14.56"), m("1164.87", "15.00")); // 14.5608..
    }

    @Test
    void exactDecimalNoFloatingPoint() {
        // 0.1 style cases that break doubles: 0.01 * 12.00 / 1200 = 0.0001 -> 0.00
        assertEquals(new BigDecimal("0.00"), m("0.01", "12.00"));
        assertEquals(new BigDecimal("1.00"), m("100.00", "12.00"));
        assertEquals(new BigDecimal("0.99"), m("99.99", "12.00")); // 0.9999 truncated
    }

    @Test
    void zeroRateOrBalance() {
        assertEquals(new BigDecimal("0.00"), m("500.00", "0.00"));
        assertEquals(new BigDecimal("0.00"), m("0.00", "15.00"));
    }

    @Test
    void negativeRateTruncatesTowardZero() {
        assertEquals(new BigDecimal("-0.12"), m("10.01", "-15.00"));
    }

    @Test
    void resultScaleIsTwo() {
        assertEquals(2, m("1.00", "1.00").scale());
    }
}
