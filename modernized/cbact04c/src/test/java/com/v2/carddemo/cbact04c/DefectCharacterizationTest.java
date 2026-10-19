package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy defects D1-D4 (preserved on purpose, CR-1 is like-for-like). */
class DefectCharacterizationTest {
    @TempDir Path tmp;

    @Test
    void d1_lastAccountIsNeverRewritten() throws IOException {
        Support.Result r = Support.afterPosting(tmp);
        List<String> out = Support.lines(r.acct());
        List<String> in = Support.lines(Support.GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"));
        assertEquals(50, out.size());
        // interest for account 50 is posted as a transaction ...
        String tx = Support.lines(r.transact()).get(49);
        assertEquals("2022071800000050", tx.substring(0, 16));
        assertEquals("0000000187G", tx.substring(132, 143));
        // ... but its account record is untouched (balance and cycle fields not updated)
        assertEquals(in.get(49), out.get(49));
        // accounts 1-49 were updated
        int changed = 0;
        for (int i = 0; i < 50; i++) if (!in.get(i).equals(out.get(i))) changed++;
        assertEquals(49, changed);
    }

    @Test
    void d2_discloureGroupNeverMatches_everyLookupFallsBackToDefault() throws IOException {
        Support.Result r = Support.afterPosting(tmp);
        long missing = r.sysout().stream().filter("DISCLOSURE GROUP RECORD MISSING"::equals).count();
        assertEquals(100, missing);
        assertEquals(100, r.sysout().stream().filter("TRY WITH DEFAULT GROUP CODE"::equals).count());
    }

    @Test
    void d3_interestIsTruncatedNotRounded() throws IOException {
        // 2339.97 * 15.00 / 1200 = 29.2496 -> 29.24 (rounding would give 29.25)
        assertEquals(new BigDecimal("29.24"),
                Cbact04c.monthlyInterest(new BigDecimal("2339.97"), new BigDecimal("15.00")));
        // golden: account 2 transaction amount 0000000292D = 29.24
        Support.Result r = Support.afterPosting(tmp);
        assertEquals("0000000292D", Support.lines(r.transact()).get(1).substring(132, 143));
    }

    @Test
    void d3_negativeBalanceTruncatesTowardZero() {
        // -10.01 * 15 / 1200 = -0.1251 -> -0.12
        BigDecimal v = Cbact04c.monthlyInterest(new BigDecimal("-10.01"), new BigDecimal("15.00"));
        assertEquals(new BigDecimal("-0.12"), v);
        assertEquals("0000000001K", Overpunch.encode(v, 11, 2));
    }

    @Test
    void d4_transactionIdCounterWrapsAfter999999() {
        assertEquals("999999", Overpunch.encodeUnsigned(999_999, 6));
        assertEquals("000000", Overpunch.encodeUnsigned(1_000_000, 6));
        assertEquals("000001", Overpunch.encodeUnsigned(1_000_001, 6));
    }

    @Test
    void d4_interestOverflowIsSilentlyTruncated() {
        // 9,999,999,999.00 * 9999.99 / 1200 ~ 8.3e10: high-order digits dropped, no error
        BigDecimal m = Cbact04c.monthlyInterest(new BigDecimal("999999999.99"), new BigDecimal("9999.99"));
        assertEquals(true, m.abs().compareTo(new BigDecimal("10000000.00")) >= 0);
        assertEquals(true, m.abs().compareTo(new BigDecimal("1000000000")) < 0);
        BigDecimal exact = new BigDecimal("999999999.99").multiply(new BigDecimal("9999.99"))
                .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.DOWN);
        assertNotEquals(exact, m);
        assertEquals(exact.remainder(new BigDecimal("1000000000")), m);
    }

    @Test
    void trailingTransactionBytesAreSpaces() throws IOException {
        Support.Result r = Support.stock(tmp);
        for (String l : Support.lines(r.transact())) {
            assertEquals(350, l.length());
            assertEquals(" ".repeat(20), l.substring(330));
        }
    }
}
