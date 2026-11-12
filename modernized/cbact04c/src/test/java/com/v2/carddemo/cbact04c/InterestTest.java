package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @TempDir Path tmp;

    private String amount(Fx.Result r, int i) { return r.tran.get(i).substring(132, 143); }

    private Fx.Result one(long balCents, long rate) throws IOException {
        // two accounts so the first one is (also) rewritten; interest tx is for account 1
        Fx f = new Fx().tcat(1, "01", "0001", balCents).tcat(2, "01", "0001", 0)
                .xref("4111111111111111", 1).xref("4222222222222222", 2)
                .acct(1, 0, 0, 0, "").acct(2, 0, 0, 0, "")
                .disc("DEFAULT", "01", "0001", rate);
        return f.run(tmp);
    }

    @Test
    void truncatesNotRounds() throws IOException {
        // 2339.97 * 15.00 / 1200 = 29.249625 -> 29.24 (half-up would be 29.25)
        Fx.Result r = one(233997, 1500);
        assertEquals("0000000292D", amount(r, 0));
    }

    @Test
    void negativeBalanceTruncatesTowardZero() throws IOException {
        assertEquals("0000000292M", amount(one(-233997, 1500), 0)); // -29.24
    }

    @Test
    void negativeSubCentInterestIsNegativeZero() throws IOException {
        assertEquals("0000000000}", amount(one(-50, 1), 0));
    }

    @Test
    void interestOverflowWrapsSilently() throws IOException {
        long bal = 99_999_999_999L, rate = 999_999;
        long expected = BigInteger.valueOf(bal).multiply(BigInteger.valueOf(rate))
                .divide(BigInteger.valueOf(120_000)).mod(BigInteger.TEN.pow(11)).longValue();
        Fx.Result r = one(bal, rate);
        assertEquals(0, r.rc);
        assertEquals(ZonedDecimal.encode(expected, 11), amount(r, 0));
        assertTrue(expected < 833_000_000_000L); // really wrapped
    }

    @Test
    void balanceOverflowWrapsSilently() throws IOException {
        Fx f = new Fx().tcat(1, "01", "0001", 120_000).tcat(2, "01", "0001", 0) // interest 1.00% -> 100 cents
                .xref("4111111111111111", 1).xref("4222222222222222", 2)
                .acct(1, 999_999_999_999L, 0, 0, "").acct(2, 0, 0, 0, "")
                .disc("DEFAULT", "01", "0001", 100);
        Fx.Result r = f.run(tmp);
        assertEquals(ZonedDecimal.encode(999_999_999_999L + 100 - 1_000_000_000_000L, 12),
                FixedRecord.get(r.acct.get(0), 13, 12));
    }
}
