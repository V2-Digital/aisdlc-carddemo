package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void roundTripsEverySignDigit() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            assertEquals(new BigDecimal("0.0" + d), Zoned.decode("000" + pos.charAt(d)));
            if (d > 0) {
                assertEquals(new BigDecimal("-0.0" + d), Zoned.decode("000" + neg.charAt(d)));
                assertEquals("000" + neg.charAt(d), Zoned.encode(new BigDecimal("-0.0" + d), 4));
            }
            assertEquals("000" + pos.charAt(d), Zoned.encode(new BigDecimal("0.0" + d), 4));
        }
    }

    @Test
    void zeroIsPositiveAndOverflowDropsHighDigits() {
        assertEquals("0000{", Zoned.encode(new BigDecimal("-0.00"), 5));
        assertEquals("0010{", Zoned.encode(new BigDecimal("10001.00"), 5));
        assertEquals(new BigDecimal("0.50"), Zoned.truncate(new BigDecimal("1000000000.50"), 9));
    }

    @Test
    void nonNumericAbends() {
        assertThrows(Abend.class, () -> Zoned.decode("00X0{"));
    }
}
