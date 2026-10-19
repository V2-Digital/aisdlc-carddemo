package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BehaviourTest {
    static final String A1 = "00000000001", A2 = "00000000002", A3 = "00000000003";
    static final List<String> X = List.of(xref("4111111111111111", A1), xref("4222222222222222", A2),
            xref("4333333333333333", A3));
    static final List<String> D = List.of(disc("DEFAULT", "01", "0001", 1800), disc("DEFAULT", "03", "0001", 0),
            disc("A000000000", "01", "0001", 2400));

    @Test void overpunchRoundTrip() {
        for (long v : new long[]{0, 1, 9, 10, -1, -9, -10, 12345678901L, -12345678901L, 1945_87}) {
            assertEquals(v, Zoned.decode(Zoned.encode(v, 12)));
        }
        assertEquals("0000000000{", Zoned.encode(0, 11));
        assertEquals(0, Zoned.decode("0000000000}"));
        assertEquals("0000000001J", Zoned.encode(-11, 11));
        assertEquals("0000000187G", Zoned.encode(1877, 11));
    }

    @Test void truncatesNotRoundsUnit() {
        // 10.05 * 18% / 12 = 0.15075 -> 0.15 ; 10.99 -> 0.16485 -> 0.16 (rounding gives 0.16/0.16? use 10.97 -> .16455 .16)
        // 11.00*18/1200 = 0.165 -> truncated 0.16, rounded 0.17
        var r = run(List.of(tcat(A1, "01", "0001", 1100), tcat(A2, "01", "0001", 0)), X, D,
                List.of(acct(A1, 0, 0, 0, ""), acct(A2, 0, 0, 0, "")));
        assertEquals(16, Zoned.decode(r.calc().transactions.get(0).substring(132, 143)));
    }

    @Test void negativeBalanceTruncatesTowardZero() {
        // -1.00 * 18 / 1200 = -0.015 -> -0.01 (not -0.02)
        var r = run(List.of(tcat(A1, "01", "0001", -100)), X, D, List.of(acct(A1, 0, 0, 0, "")));
        assertEquals(-1, Zoned.decode(r.calc().transactions.get(0).substring(132, 143)));
        // tiny negative -> -0 normalised to '{'
        r = run(List.of(tcat(A1, "01", "0001", -1)), X, D, List.of(acct(A1, 0, 0, 0, "")));
        assertEquals("0000000000{", r.calc().transactions.get(0).substring(132, 143));
    }

    @Test void lastAccountNotRewritten_D1() {
        String a1 = acct(A1, 10000, 500, -300, ""), a2 = acct(A2, 194587, 150175, -4788, "");
        var r = run(List.of(tcat(A1, "01", "0001", 10000), tcat(A2, "01", "0001", 10000)), X, D, List.of(a1, a2));
        assertNull(r.abend());
        assertEquals(2, r.calc().transactions.size());          // interest was posted for both
        assertNotEquals(a1, r.calc().accounts.get(0));          // first rewritten
        assertEquals(10000 + 150, Records.acctBal(r.calc().accounts.get(0)));
        assertEquals(a2, r.calc().accounts.get(1));             // last: untouched, cycle fields intact
        assertEquals(194587, Records.acctBal(r.calc().accounts.get(1)));
    }

    @Test void defaultFallback() {
        var r = run(List.of(tcat(A1, "01", "0001", 120000)), X, D, List.of(acct(A1, 0, 0, 0, "NOSUCH")));
        assertTrue(r.out().contains("DISCLOSURE GROUP RECORD MISSING"));
        assertTrue(r.out().contains("TRY WITH DEFAULT GROUP CODE"));
        assertEquals(1800, Zoned.decode(r.calc().transactions.get(0).substring(132, 143)));
    }

    @Test void directGroupHit() {
        var r = run(List.of(tcat(A1, "01", "0001", 120000)), X, D, List.of(acct(A1, 0, 0, 0, "A000000000")));
        assertFalse(r.out().contains("DISCLOSURE GROUP RECORD MISSING"));
        assertEquals(2400, Zoned.decode(r.calc().transactions.get(0).substring(132, 143)));
    }

    @Test void zeroRateNoTranButRewrite() {
        String a1 = acct(A1, 5000, 700, -200, ""), a2 = acct(A2, 0, 0, 0, "");
        var r = run(List.of(tcat(A1, "03", "0001", 5000), tcat(A2, "03", "0001", 0)), X, D, List.of(a1, a2));
        assertEquals(0, r.calc().transactions.size());
        String n = r.calc().accounts.get(0);
        assertEquals(5000, Records.acctBal(n));
        assertEquals(0, Zoned.decode(n.substring(78, 90)));
        assertEquals(0, Zoned.decode(n.substring(90, 102)));
        assertNotEquals(a1, n);
    }

    @Test void globalCounterTypeCatClock() {
        var r = run(List.of(tcat(A1, "01", "0001", 10000), tcat(A2, "01", "0001", 10000), tcat(A3, "01", "0001", 10000)),
                X, D, List.of(acct(A1, 0, 0, 0, ""), acct(A2, 0, 0, 0, ""), acct(A3, 0, 0, 0, "")));
        List<String> t = r.calc().transactions;
        assertEquals(3, t.size());
        for (int i = 0; i < 3; i++) {
            String s = t.get(i);
            assertEquals(350, s.length());
            assertEquals("2022071800" + String.format("%06d", i + 1), s.substring(0, 16));
            assertEquals("010005System    ", s.substring(16, 32));
            assertTrue(s.substring(32).startsWith("Int. for a/c 0000000000" + (i + 1)));
            assertEquals("2022-07-18-00.00.00.000000", s.substring(s.length() - 20 - 52, s.length() - 20 - 26));
            assertEquals(s.substring(s.length() - 20 - 52, s.length() - 20 - 26), s.substring(s.length() - 46, s.length() - 20));
            assertEquals(" ".repeat(20), s.substring(330));
        }
        assertEquals("4222222222222222", t.get(1).substring(262, 278));
        // injected non-midnight clock with hundredths
        var c = new InterestCalc("2022071800", Main.clock("2023010203040567"), x -> {}, List.of(tcat(A1, "01", "0001", 10000)),
                List.of(Records.pad(X.get(0), 50)), D, List.of(acct(A1, 0, 0, 0, "")));
        c.run();
        assertTrue(c.transactions.get(0).contains("2023-01-02-03.04.05.670000"));
    }

    @Test void untouchedAccountsAndFiller() {
        String a0 = acct("00000000009", 1, 2, 3, ""), a1 = acct(A1, 10000, 5, 5, ""), a2 = acct(A2, 1, 1, 1, "");
        var r = run(List.of(tcat(A1, "01", "0001", 10000), tcat(A2, "01", "0001", 10000)), X, D, List.of(a0, a1, a2));
        assertEquals(a0, r.calc().accounts.get(0));
        String n = r.calc().accounts.get(1);
        assertEquals(300, n.length());
        assertEquals(a1.substring(0, 12), n.substring(0, 12));
        assertEquals(a1.substring(24, 78), n.substring(24, 78));
        assertEquals(a1.substring(102), n.substring(102));
        assertTrue(n.substring(112).startsWith("          FILLERFILLER"));
    }

    @Test void emptyTcatbal() {
        var r = run(List.of(), X, D, List.of(acct(A1, 5, 5, 5, "")));
        assertNull(r.abend());
        assertEquals(0, r.calc().transactions.size());
    }

    @Test void abendMissingAccount() {
        var r = run(List.of(tcat("00000000099", "01", "0001", 100)), X, D, List.of(acct(A1, 0, 0, 0, "")));
        assertNotNull(r.abend());
        assertTrue(r.out().contains("ACCOUNT NOT FOUND: 00000000099"));
        assertTrue(r.out().contains("ABENDING PROGRAM"));
        assertFalse(r.out().contains("END OF EXECUTION OF PROGRAM CBACT04C"));
    }

    @Test void abendMissingXref() {
        var r = run(List.of(tcat(A1, "01", "0001", 100)), List.of(xref("4", A2)), D, List.of(acct(A1, 0, 0, 0, "")));
        assertNotNull(r.abend());
    }

    @Test void abendMissingDefaultRate() {
        var r = run(List.of(tcat(A1, "02", "0009", 100)), X, D, List.of(acct(A1, 0, 0, 0, "")));
        assertNotNull(r.abend());
        assertTrue(r.out().contains("ABENDING PROGRAM"));
    }

    /** D3: high-order digits silently dropped, no exception, no widening. */
    @Test void overflowDropsHighOrderDigits_D3() {
        // monthly: 999,999,999.99 * 9999.99% / 12 = ~8.33e9 -> low 11 digits kept
        long bal = 99_999_999_999L, rate = 999_999;
        long exp = (bal * rate / 120000) % 100_000_000_000L;
        assertTrue(bal * rate / 120000 > 99_999_999_999L);
        var r = run(List.of(tcat(A1, "01", "0001", bal), tcat(A2, "01", "0001", 0)), X, List.of(disc("DEFAULT", "01", "0001", rate)),
                List.of(acct(A1, 0, 0, 0, ""), acct(A2, 0, 0, 0, "")));
        assertNull(r.abend());
        assertEquals(exp, Zoned.decode(r.calc().transactions.get(0).substring(132, 143)));
        // balance wraps at 10 integer digits
        var r2 = run(List.of(tcat(A1, "01", "0001", 120000_00), tcat(A2, "01", "0001", 0)), X, D,
                List.of(acct(A1, 999_999_999_999L, 0, 0, ""), acct(A2, 0, 0, 0, "")));
        assertEquals((999_999_999_999L + 1800_00L) % 1_000_000_000_000L, Records.acctBal(r2.calc().accounts.get(0)));
    }

    @Test void counterWrapsAt999999_D6() {
        // direct check of the wrap rule via many records is costly; 1,000,000 tcatbal rows for one account
        var rows = new java.util.ArrayList<String>();
        String row = tcat(A1, "01", "0001", 100);
        for (int i = 0; i < 1_000_001; i++) rows.add(row);
        var c = new InterestCalc("2022071800", CLOCK, s -> {}, rows, List.of(Records.pad(X.get(0), 50)), D,
                List.of(acct(A1, 0, 0, 0, "")));
        c.run();
        assertEquals("2022071800999999", c.transactions.get(999_998).substring(0, 16));
        assertEquals("2022071800000000", c.transactions.get(999_999).substring(0, 16));
        assertEquals("2022071800000001", c.transactions.get(1_000_000).substring(0, 16));
    }
}
