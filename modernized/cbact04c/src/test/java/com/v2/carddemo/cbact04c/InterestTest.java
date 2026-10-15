package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** D2 truncation, DEFAULT fallback, rate-0 skip. Account 1 is followed by account 2 so account 1 gets rewritten (D1). */
class InterestTest {
    private Fixtures.Result run(Path tmp, String bal, String rate, String group, String... discExtra) throws Exception {
        Path t = Fixtures.write(tmp, "t.txt", Fixtures.tcat("00000000001", "01", "0001", bal),
                Fixtures.tcat("00000000002", "01", "0001", "0000000000{"));
        Path x = Fixtures.write(tmp, "x.txt", Fixtures.xref("4111111111111111", "00000000001"),
                Fixtures.xref("4222222222222222", "00000000002"));
        Path a = Fixtures.write(tmp, "a.txt", Fixtures.acct("00000000001", "00000001000{", group),
                Fixtures.acct("00000000002", "00000001000{", ""));
        String[] disc = new String[1 + discExtra.length];
        disc[0] = Fixtures.disc("DEFAULT", "01", "0001", rate);
        System.arraycopy(discExtra, 0, disc, 1, discExtra.length);
        Path d = Fixtures.write(tmp, "d.txt", disc);
        return Fixtures.runMain(tmp.resolve("out"), t, x, a, d);
    }

    private List<String> trans(Fixtures.Result r) throws Exception { return Fixtures.lines(r.out().resolve("TRANSACT.txt")); }

    private String amount(String tran) { return tran.substring(132, 143); }

    private String acct1(Fixtures.Result r) throws Exception { return Fixtures.lines(r.out().resolve("ACCTFILE.txt")).get(0); }

    @Test
    void D2_truncates_1164_87_at_15_00_gives_14_56(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = run(tmp, "0000011648G", "00150{", "");
        assertEquals(0, r.rc());
        assertEquals("0000000145F", amount(trans(r).get(0)));
    }

    @Test
    void D2_truncatesWhereRoundingWouldDiffer(@TempDir Path tmp) throws Exception {
        // 100.00 * 11.99 / 1200 = 0.99916 -> 0.99 (rounding would give 1.00)
        assertEquals("0000000009I", amount(trans(run(tmp, "0000001000{", "00119I", "")).get(0)));
    }

    @Test
    void negativeBalanceTruncatesTowardZero(@TempDir Path tmp) throws Exception {
        assertEquals("0000000009R", amount(trans(run(tmp, "0000001000}", "00119I", "")).get(0)));
    }

    @Test
    void negativeResultThatTruncatesToZeroIsEncodedPositive(@TempDir Path tmp) throws Exception {
        // -0.01 * 1.00 / 1200 = -0.0000083 -> 0.00 ; rate non-zero so a transaction is still written
        Fixtures.Result r = run(tmp, "0000000000J", "00010{", "");
        assertEquals("0000000000{", amount(trans(r).get(0)));
    }

    @Test
    void rateZeroWritesNoTransactionButStillRewritesAccount(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = run(tmp, "0000011648G", "00000{", "");
        assertEquals(0, trans(r).size());
        String a = acct1(r);
        assertEquals("00000001000{", a.substring(12, 24)); // unchanged balance
        assertEquals("00000000000{", a.substring(78, 90)); // cycle credit zeroed
        assertEquals("00000000000{", a.substring(90, 102)); // cycle debit zeroed
    }

    @Test
    void rewriteAddsInterestAndZeroesCycleFields(@TempDir Path tmp) throws Exception {
        String a = acct1(run(tmp, "0000011648G", "00150{", ""));
        assertEquals("00000001145F", a.substring(12, 24)); // 100.00 + 14.56
        assertEquals("00000000000{", a.substring(78, 90));
        assertEquals("00000000000{", a.substring(90, 102));
    }

    @Test
    void defaultGroupFallbackWhenGroupMissing(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = run(tmp, "0000011648G", "00150{", "");
        assertTrue(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING"));
        assertTrue(r.sysout().contains("TRY WITH DEFAULT GROUP CODE"));
        assertEquals("0000000145F", amount(trans(r).get(0)));
    }

    @Test
    void specificGroupRateUsedWithoutFallback(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = run(tmp, "0000011648G", "00150{", "GOLD", Fixtures.disc("GOLD", "01", "0001", "00300{"));
        // only account 2 (blank group, zero balance) falls back
        assertEquals(1, r.sysout().stream().filter("DISCLOSURE GROUP RECORD MISSING"::equals).count());
        assertEquals("0000000291B", amount(trans(r).get(0))); // 1164.87 * 30.00 / 1200 = 29.12
    }
}
