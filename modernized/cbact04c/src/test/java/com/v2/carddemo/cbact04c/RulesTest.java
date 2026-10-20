package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Interest arithmetic, business rules and characterization of preserved defects (D1, D7, D9). */
class RulesTest {
    static final String CYC_CR = "00000005000{";
    static final String CYC_DB = "00000002000{";
    static final String ZERO12 = "00000000000{";

    static String acct(int n, String bal, String group) {
        return Fx.acct(String.format("%011d", n), bal, CYC_CR, CYC_DB, group);
    }

    static String tcat(int n, String type, String bal) {
        return Fx.tcat(String.format("%011d", n), type, "0001", bal);
    }

    static String xref(int n) {
        return Fx.xref(String.valueOf(n).repeat(16), String.format("%011d", n));
    }

    static final List<String> DISC = List.of(Fx.disc("DEFAULT", "01", "0001", "00150{"),
            Fx.disc("DEFAULT", "03", "0001", "00000{"), Fx.disc("GRP1", "01", "0001", "00300{"));

    static Fx run(Path tmp, List<String> tcat, List<String> accts, List<String> xrefs, List<String> disc)
            throws IOException {
        return new Fx(tmp).dd("TCATBALF", tcat.toArray(String[]::new)).dd("XREFFILE", xrefs.toArray(String[]::new))
                .dd("ACCTFILE", accts.toArray(String[]::new)).dd("DISCGRP", disc.toArray(String[]::new)).run();
    }

    /** acct 1 (default group), acct 2 (GRP1), acct 3 (last, so D1 does not hide it) */
    static Fx three(Path tmp, String bal1, String tcat1) throws IOException {
        return run(tmp,
                List.of(tcat(1, "01", tcat1), tcat(1, "03", "0000010000{"), tcat(2, "01", "0000010000{"),
                        tcat(3, "01", "0000010000{")),
                List.of(acct(1, bal1, ""), acct(2, "00000001000{", "GRP1"),
                        acct(3, "00000001000{", "")),
                List.of(xref(1), xref(2), xref(3)), DISC);
    }

    static String amount(String txLine) {
        return txLine.substring(132, 143);
    }

    // ---- AC5, AC9: truncation toward zero ------------------------------------------------
    @Test
    void truncatesNotRounds_halfUpWouldGive013(@TempDir Path tmp) throws IOException {
        // 10.04 * 1.50 / 12 = 0.1255 -> 0.12 (half-up: 0.13)
        Fx fx = three(tmp, "00000001000{", "0000000100D");
        assertEquals("0000000001B", amount(fx.lines("TRANSACT").get(0)));
        assertEquals("00000001001B", fx.lines("ACCTFILE").get(0).substring(12, 24)); // 100.00 + 0.12
    }

    @Test
    void negativeBalanceTruncatesTowardZero(@TempDir Path tmp) throws IOException {
        // -10.04 -> -0.1255 -> -0.12 (J..R overpunch: K = -2)
        Fx fx = three(tmp, "00000001000{", "0000000100M");
        assertEquals("0000000001K", amount(fx.lines("TRANSACT").get(0)));
        assertEquals("00000000998H", fx.lines("ACCTFILE").get(0).substring(12, 24)); // 100.00 - 0.12 = 99.88
    }

    @Test
    void tinyNegativeInterestBecomesPositiveZero(@TempDir Path tmp) throws IOException {
        Fx fx = three(tmp, "00000001000{", "0000000003}"); // -0.30 * 1.5 / 12 = -0.000375
        assertEquals("0000000000{", amount(fx.lines("TRANSACT").get(0)));
    }

    // ---- AC7 ---------------------------------------------------------------------------
    @Test
    void defaultGroupFallbackAndRealGroupHit(@TempDir Path tmp) throws IOException {
        Fx fx = three(tmp, "00000001000{", "0000000100D");
        List<String> sys = fx.lines("SYSOUT");
        // accts 1 (two cats) and 3 fall back; acct 2 hits GRP1 directly: 3 misses in total
        assertEquals(3, sys.stream().filter("DISCLOSURE GROUP RECORD MISSING"::equals).count());
        assertEquals(3, sys.stream().filter("TRY WITH DEFAULT GROUP CODE"::equals).count());
        // GRP1 rate 30: 1000.00 * 30 / 1200 = 25.00 (default 15 would give 12.50)
        assertEquals("0000000250{", amount(fx.lines("TRANSACT").get(1)));
        assertEquals("0000000125{", amount(fx.lines("TRANSACT").get(2)));
    }

    @Test
    void zeroRateWritesNoTransactionAndIdsRunAcrossAccounts(@TempDir Path tmp) throws IOException {
        Fx fx = three(tmp, "00000001000{", "0000000100D");
        List<String> tx = fx.lines("TRANSACT");
        assertEquals(3, tx.size()); // 4 TCATBAL rows, the type-03 row has rate 0
        for (int i = 0; i < 3; i++) {
            assertEquals(Fx.PARM + String.format("%06d", i + 1), tx.get(i).substring(0, 16));
            assertEquals(350, tx.get(i).length());
        }
        assertEquals("01" + "0005" + "System    Int. for a/c 00000000002", tx.get(1).substring(16, 16 + 6 + 10 + 13 + 11));
    }

    @Test
    void rewriteChangesOnlyBalanceAndCycleFields(@TempDir Path tmp) throws IOException {
        Fx fx = three(tmp, "00000001000{", "0000000100D");
        List<String> before = new ArrayList<>();
        for (String l : java.nio.file.Files.readAllLines(fx.in.get("ACCTFILE"))) {
            before.add(l);
        }
        List<String> after = fx.lines("ACCTFILE");
        for (int a = 0; a < 2; a++) { // third account is the D1 victim
            for (int i = 0; i < 300; i++) {
                if (before.get(a).charAt(i) != after.get(a).charAt(i)) {
                    assertTrue((i >= 12 && i < 24) || (i >= 78 && i < 102),
                            "acct " + (a + 1) + " byte " + i + " changed outside balance/cycle fields");
                }
            }
            assertEquals(ZERO12 + ZERO12, after.get(a).substring(78, 102));
        }
    }

    // ---- AC6: D1 -----------------------------------------------------------------------
    @Test
    void D1_lastAccountIsNeverRewritten(@TempDir Path tmp) throws IOException {
        Fx fx = three(tmp, "00000001000{", "0000000100D");
        String last = fx.lines("ACCTFILE").get(2);
        assertEquals(acct(3, "00000001000{", ""), last); // balance and cycle fields untouched
        assertEquals(3, fx.lines("TRANSACT").size()); // ...even though its interest tx was written
        assertEquals("0000000125{", amount(fx.lines("TRANSACT").get(2)));
    }

    // ---- D7, D8, D9 --------------------------------------------------------------------
    @Test
    void D7_balanceOverflowSilentlyLosesHighOrderDigit(@TempDir Path tmp) throws IOException {
        Fx fx = three(tmp, "99999999999I", "0000000100D"); // 9,999,999,999.99 + 0.12 (interest 0.12 -> wraps)
        assertEquals("00000000001A", fx.lines("ACCTFILE").get(0).substring(12, 24)); // 10000000000.11 wraps to 0.11
    }

    @Test
    void D8_missingDiscgrpFileReportsDalyRejectsMessage(@TempDir Path tmp) throws IOException {
        Fx fx = three(tmp, "00000001000{", "0000000100D");
        java.nio.file.Files.delete(fx.in.get("DISCGRP"));
        fx.run();
        assertEquals(16, fx.rc);
        assertTrue(fx.lines("SYSOUT").contains("ERROR OPENING DALY REJECTS FILE"));
    }

    @Test
    void D9_accountWithOnlyZeroRatesIsStillRewritten(@TempDir Path tmp) throws IOException {
        Fx fx = run(tmp, List.of(tcat(1, "03", "0000010000{"), tcat(2, "03", "0000010000{")),
                List.of(acct(1, "00000001000{", ""), acct(2, "00000001000{", "")), List.of(xref(1), xref(2)), DISC);
        assertEquals(0, fx.lines("TRANSACT").size());
        assertEquals(ZERO12 + ZERO12, fx.lines("ACCTFILE").get(0).substring(78, 102)); // cycle zeroed
        assertEquals(CYC_CR + CYC_DB, fx.lines("ACCTFILE").get(1).substring(78, 102)); // D1: last untouched
    }
}
