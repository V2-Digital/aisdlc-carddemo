package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InterestTest {
    private static String interest(String bal, String rate) {
        BigDecimal i = new BigDecimal(bal).multiply(new BigDecimal(rate)).divide(BigDecimal.valueOf(1200), 10,
                java.math.RoundingMode.DOWN);
        return Zoned.truncate(i, 9, 2).toPlainString();
    }

    @Test
    void truncatedNotRounded() {
        assertEquals("14.56", interest("1164.87", "15.00")); // 14.560875
        assertEquals("0.00", interest("0.99", "1.00"));
    }

    @Test
    void negativeTruncatesTowardZero() {
        assertEquals("-14.56", interest("-1164.87", "15.00"));
        assertEquals("0.00", interest("-0.01", "15.00"));
    }
}
