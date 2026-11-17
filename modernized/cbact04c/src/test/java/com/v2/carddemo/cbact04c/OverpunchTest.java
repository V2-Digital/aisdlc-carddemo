package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OverpunchTest {
    @Test
    void roundTripsAllTwentySignCharacters() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            BigDecimal p = Overpunch.decode("000000000" + "0" + pos.charAt(d), 2);
            assertEquals(new BigDecimal("0.0" + d), p);
            assertEquals("000" + pos.charAt(d), Overpunch.encode(p, 2, 4));
            if (d > 0) {
                BigDecimal n = Overpunch.decode("000" + neg.charAt(d), 2);
                assertEquals(new BigDecimal("-0.0" + d), n);
                assertEquals("000" + neg.charAt(d), Overpunch.encode(n, 2, 4));
            }
        }
    }

    @Test
    void zeroEncodesAsBraceAndNegativeZeroDecodesToZero() {
        assertEquals("00000000000{", Overpunch.encode(BigDecimal.ZERO, 2, 12));
        assertEquals(0, Overpunch.decode("000}", 2).signum());
        assertEquals("000{", Overpunch.encode(Overpunch.decode("000}", 2), 2, 4));
    }

    @Test
    void plainDigitDecodesAsPositive() {
        assertEquals(new BigDecimal("1.25"), Overpunch.decode("125", 2));
    }
}
