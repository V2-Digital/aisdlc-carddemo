package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Fixture.*;
import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InterestTest {
    static final String DEFAULT15 = disc("DEFAULT", "01", "0001", "001500");

    @Test
    void truncatesTowardZeroNotRounds() throws Exception {
        // 116.48 * 15% / 12 = 1.456 -> 1.45 (rounding would give 1.46)
        Result r = run("2022071800", inputs("0000001164H", "00000019458G", "", DEFAULT15));
        assertEquals("0000000014E", amount(firstTran(r)));
    }

    @Test
    void negativeBalanceTruncatesTowardZero() throws Exception {
        // -116.48 -> -1.45 (FLOOR would give -1.46)
        Result r = run("2022071800", inputs("0000001164Q", "00000019458G", "", DEFAULT15));
        assertEquals("0000000014N", amount(firstTran(r)));
    }

    @Test
    void realGroupRateIsUsedWithoutMissingMessage() throws Exception {
        // F5: the group path is never hit by golden data. 116.48 * 12.00 / 1200 = 1.1648 -> 1.16
        String rows = disc("GRP1", "01", "0001", "00120{".replace("{", "0")) + DEFAULT15;
        Result r = run("2022071800", inputs("0000001164H", "00000019458G", "GRP1", rows));
        // only account 2 (blank group) falls back; account 1 hits GRP1
        assertEquals(1, r.sysout().split("DISCLOSURE GROUP RECORD MISSING", -1).length - 1);
        assertEquals("0000000011F", amount(firstTran(r)));
    }

    @Test
    void missingGroupFallsBackToDefault() throws Exception {
        Result r = run("2022071800", inputs("0000001164H", "00000019458G", "NOPE", DEFAULT15));
        assertEquals(0, r.rc());
        assertTrue(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
    }

    @Test
    void overflowWrapsSilently() throws Exception {
        // F3: 999999999.99 * 9999.99 / 1200 = 8333324999.99... exceeds S9(9)V99; high digit dropped
        Result r = run("2022071800", inputs("9999999999I", "00000019458G", "", disc("DEFAULT", "01", "0001", "99999I")));
        assertEquals(0, r.rc());
        BigDecimal exact = new BigDecimal("999999999.99").multiply(new BigDecimal("9999.99"))
                .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.DOWN);
        BigDecimal wrapped = new BigDecimal(exact.unscaledValue().remainder(java.math.BigInteger.TEN.pow(11)), 2);
        assertNotEquals(exact, wrapped);
        assertEquals(Zoned.encode(wrapped, 11), amount(firstTran(r)));
    }
}
