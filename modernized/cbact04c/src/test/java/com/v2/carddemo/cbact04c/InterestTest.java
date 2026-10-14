package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InterestTest {
    private static BigDecimal i(String bal, String rate) {
        return Cbact04c.interest(new BigDecimal(bal), new BigDecimal(rate));
    }

    @Test
    void truncatesNotRounds_D3() {
        assertEquals(new BigDecimal("14.56"), i("1164.87", "15.00")); // 14.5608..
        assertEquals(new BigDecimal("0.00"), i("0.01", "0.01"));
        assertEquals(new BigDecimal("0.01"), i("0.99", "15.00")); // .012375, a rounding impl gives .01 too
        assertEquals(new BigDecimal("0.01"), i("1.59", "10.00")); // .01325
        assertEquals(new BigDecimal("0.00"), i("0.79", "15.00")); // .009875 would round to .01
    }

    @Test
    void negativeBalanceTruncatesTowardZero_D5() {
        assertEquals(new BigDecimal("-14.56"), i("-1164.87", "15.00"));
        assertEquals(new BigDecimal("0.00"), i("-0.79", "15.00"));
    }

    @Test
    void overflowDropsHighOrderDigits_D6() {
        // 999999999.99 * 9999.99 / 1200 = 8333324999.99.. -> keep S9(9)V99 low-order digits
        BigDecimal exact = new BigDecimal("999999999.99").multiply(new BigDecimal("9999.99"))
                .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.DOWN);
        BigDecimal expected = exact.remainder(new BigDecimal("1000000000"));
        assertEquals(expected, i("999999999.99", "9999.99"));
    }
}
