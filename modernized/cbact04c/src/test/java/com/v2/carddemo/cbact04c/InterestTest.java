package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** COBOL-checked cases live in legacy/tests/cbact04c-edge.sh; the values here are pinned from it. */
class InterestTest {
    private static BigDecimal i(String bal, String rate) {
        return Cbact04c.interest(new BigDecimal(bal), new BigDecimal(rate));
    }

    @Test
    void positiveTruncatesNotRounds() {
        assertEquals("14.56", i("1164.87", "15.00").toPlainString()); // 14.5608
        assertEquals("0.00", i("0.01", "0.01").toPlainString());
        assertEquals("0.00", i("1000.00", "0.01").toPlainString()); // 0.00833
        assertEquals("0.98", i("99.99", "11.88").toPlainString()); // 0.98990
    }

    @Test
    void negativeTruncatesTowardZero() {
        assertEquals("-14.56", i("-1164.87", "15.00").toPlainString());
        assertEquals("0.00", i("-0.01", "15.00").toPlainString());
    }

    @Test
    void zeroBalance() {
        assertEquals("0.00", i("0.00", "15.00").toPlainString());
    }

    @Test
    void largestBalanceFitsIn9Digits() {
        BigDecimal r = ZonedDecimal.truncate(i("9999999999.99", "9999.99"), 9, 2);
        assertTrue(r.abs().compareTo(new BigDecimal("999999999.99")) <= 0);
    }
}
