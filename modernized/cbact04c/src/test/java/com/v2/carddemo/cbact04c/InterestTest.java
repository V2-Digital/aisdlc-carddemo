package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    private static String amount(Fx f, int i) {
        return Records.slice(f.transact.get(i), 133, 11);
    }

    @Test
    void truncatesNotRounds(@TempDir Path t) throws Exception {
        // 116.48 * 15 / 1200 = 1.456 -> 1.45 (rounding would give 1.46)
        Fx f = Fx.twoAccounts("0000001164H", "00150{").run(t);
        assertEquals(0, f.rc);
        assertEquals("0000000014E", amount(f, 0));
    }

    @Test
    void negativeBalanceTruncatesTowardZero(@TempDir Path t) throws Exception {
        // -10.01 * 15 / 1200 = -0.12512 -> -0.12 (floor would give -0.13)
        Fx f = Fx.twoAccounts("0000000100J", "00150{").run(t);
        assertEquals("0000000001K", amount(f, 0));
    }

    @Test
    void overflowWrapsSilently(@TempDir Path t) throws Exception {
        // 999999999.99 * 9999.99 / 1200 has more than 9 integer digits: high-order digits dropped, rc 0
        Fx f = Fx.twoAccounts("9999999999I", "99999I").run(t);
        assertEquals(0, f.rc);
        java.math.BigDecimal exact = new java.math.BigDecimal("999999999.99")
                .multiply(new java.math.BigDecimal("9999.99")).divide(java.math.BigDecimal.valueOf(1200), 2,
                        java.math.RoundingMode.DOWN);
        java.math.BigDecimal want = Zoned.fit(exact, 9);
        assertEquals(Zoned.encode(want, 2, 11), amount(f, 0));
    }
}
