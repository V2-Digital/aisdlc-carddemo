package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    /** Interest amount (TRANSACT 132:11) for account 1 with the given balance; account 2 is the unrewritten last one. */
    private String amountOf(Path tmp, String bal) throws Exception {
        var r = Fx.run(tmp, Map.of(), Map.of("TCATBALF", Fx.tcat("00000000001", "01", "0001", bal)
                + Fx.tcat("00000000002", "01", "0001", "0000000000{")));
        assertEquals(0, r.rc());
        return r.lines("TRANSACT.txt").get(0).substring(132, 143);
    }

    @Test
    void interestTruncatesNotRounds_2339_97_at_15pct_is_29_24(@TempDir Path tmp) throws Exception {
        // 2339.97 * 15 / 1200 = 29.2496 -> 29.24 (F2)
        assertEquals("0000000292D", amountOf(tmp, "0000023399G"));
    }

    @Test
    void truncationIsTowardZeroForNegative(@TempDir Path tmp) throws Exception {
        // -1.00 * 15 / 1200 = -0.0125 -> -0.01 (F9)
        assertEquals("0000000000J", amountOf(tmp, "0000000010}"));
    }

    @Test
    void tinyNegativeTruncatesToZeroWrittenWithPositiveSign(@TempDir Path tmp) throws Exception {
        // -0.50 * 15 / 1200 = -0.00625 -> 0.00, written as '{' (confirmed against COBOL by cbact04c-edge.sh)
        assertEquals("0000000000{", amountOf(tmp, "0000000005}"));
    }

    @Test
    void overflowWrapsModuloKeepingLowDigits_F6() {
        BigDecimal big = new BigDecimal("999999999.99").multiply(new BigDecimal("9999.99"))
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN);
        assertEquals(big.unscaledValue().mod(BigInteger.TEN.pow(11)), Zoned.wrap(big, 11).unscaledValue());
        assertEquals("0000000000A", Zoned.encode(new BigDecimal("1000000000.01"), 11));
    }
}
