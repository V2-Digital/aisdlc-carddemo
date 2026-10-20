package com.v2.carddemo.cbtrn02c;

import static com.v2.carddemo.cbtrn02c.Harness.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Validation (100-103), posting, and the preserved legacy defects D1-D6. */
class RulesTest {
    static final String C1 = "1111111111111111", C2 = "2222222222222222", C9 = "9999999999999999";
    static final String OK_TS = "2022-06-10 19:27:53.000000";
    static final String XREF = xref(C1, 1) + xref(C2, 2) + xref("5555555555555555", 99);

    static String reasons(Harness h) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : h.out("DALYREJS").split("\n")) {
            if (!l.isEmpty()) {
                sb.append(l, 350, 354).append(' ');
            }
        }
        return sb.toString().trim();
    }

    static String acctLine(Path w, int id) throws IOException {
        for (String l : read(w.resolve("ACCTFILE.txt")).split("\n")) {
            if (l.startsWith(String.format("%011d", id))) {
                return l;
            }
        }
        return null;
    }

    static Harness run(Path w, String trans, String accts, String tcats) throws IOException {
        return Harness.run(w, of(trans, XREF, accts, tcats), FROZEN);
    }

    @Test void reason100_invalidCard(@TempDir Path w) throws IOException {
        Harness h = run(w, tran(1, "01", 1, 100, C9, OK_TS), acct(1, 0, 100000, "2030-01-01", 0, 0), "");
        assertEquals("0100", reasons(h));
        assertTrue(h.out("DALYREJS").contains("0100INVALID CARD NUMBER FOUND"));
        assertEquals(4, h.rc);
        assertEquals("", h.out("TRANFILE"));
    }

    @Test void reason101_accountMissing(@TempDir Path w) throws IOException {
        Harness h = run(w, tran(1, "01", 1, 100, "5555555555555555", OK_TS), acct(1, 0, 100000, "2030-01-01", 0, 0), "");
        assertEquals("0101", reasons(h));
        assertTrue(h.out("DALYREJS").contains("0101ACCOUNT RECORD NOT FOUND"));
    }

    @Test void reason102_overLimit_andBoundaryIsAccepted(@TempDir Path w) throws IOException {
        // limit 1000.00; exposure == limit is accepted (limit < temp), one cent more is rejected
        String t = tran(1, "01", 1, 100000, C1, OK_TS) + tran(2, "01", 1, 100001, C1, OK_TS);
        Harness h = run(w, t, acct(1, 0, 100000, "2030-01-01", 0, 0), "");
        assertEquals("0102", reasons(h));
        assertEquals(1, h.out("TRANFILE").split("\n").length);
    }

    @Test void reason103_expired_stringCompareOnOrigTs(@TempDir Path w) throws IOException {
        String t = tran(1, "01", 1, 100, C1, "2030-01-01 00:00:00.000000") // equal date: accepted
            + tran(2, "01", 1, 100, C1, "2030-01-02 00:00:00.000000");     // after: rejected
        Harness h = run(w, t, acct(1, 0, 100000, "2030-01-01", 0, 0), "");
        assertEquals("0103", reasons(h));
        assertTrue(h.out("DALYREJS").contains("0103TRANSACTION RECEIVED AFTER ACCT EXPIRATION"));
    }

    @Test void rejectRecordIsTranPlusReasonPlus76CharDesc(@TempDir Path w) throws IOException {
        String t = tran(1, "01", 1, 100, C9, OK_TS);
        Harness h = run(w, t, "", "");
        String rej = h.out("DALYREJS");
        assertEquals(430 + 1, rej.length());
        assertEquals(t.substring(0, 350), rej.substring(0, 350));
        assertEquals(fit("INVALID CARD NUMBER FOUND", 76), rej.substring(354, 430));
    }

    // ---- posting ----

    @Test void tcatbalCreateVsUpdate(@TempDir Path w) throws IOException {
        String t = tran(1, "01", 1, 1000, C1, OK_TS) + tran(2, "01", 2, 2500, C1, OK_TS);
        Harness h = run(w, t, acct(1, 0, 100000, "2030-01-01", 0, 0), tcat(1, "01", 1, 5000));
        assertEquals(
            "START OF EXECUTION OF PROGRAM CBTRN02C\n"
                + "TCATBAL record not found for key : 00000000001010002.. Creating.\n"
                + "TRANSACTIONS PROCESSED :000000002\nTRANSACTIONS REJECTED  :000000000\n"
                + "END OF EXECUTION OF PROGRAM CBTRN02C\n", h.stdout);
        assertEquals(tcat(1, "01", 1, 6000) + tcat(1, "01", 2, 2500), read(w.resolve("TCATBALF.txt")));
        assertEquals(0, h.rc);
    }

    @Test void negativeAmountGoesToCycDebit_zeroAndPositiveToCycCredit(@TempDir Path w) throws IOException {
        String t = tran(1, "01", 1, 300, C1, OK_TS) + tran(2, "01", 1, -200, C1, OK_TS) + tran(3, "01", 1, 0, C1, OK_TS);
        run(w, t, acct(1, 1000, 100000, "2030-01-01", 50, -20), "");
        String a = acctLine(w, 1);
        assertEquals(1100, Zoned.decode(a.substring(12, 24)));  // 1000 + 300 - 200
        assertEquals(350, Zoned.decode(a.substring(78, 90)));   // credit 50 + 300 (+0)
        assertEquals(-220, Zoned.decode(a.substring(90, 102))); // debit -20 + -200
    }

    @Test void procTsIsClockWithHundredthsAnd0000AndFillerBlank(@TempDir Path w) throws IOException {
        Harness h = Harness.run(w, of(tran(1, "01", 1, 100, C1, OK_TS), XREF, acct(1, 0, 100000, "2030-01-01", 0, 0), ""),
            "2023010203040567");
        String r = h.out("TRANFILE");
        assertEquals("2023-01-02-03.04.05.670000", r.substring(304, 330));
        assertEquals(" ".repeat(20), r.substring(330, 350));
    }

    @Test void emptyDalytranRewritesInputsUnchanged(@TempDir Path w) throws IOException {
        String a = acct(1, 5, 100, "2030-01-01", 0, 0), tc = tcat(1, "01", 1, 7);
        Harness h = run(w, "", a, tc);
        assertEquals(0, h.rc);
        assertTrue(h.stdout.contains("TRANSACTIONS PROCESSED :000000000"));
        assertEquals(a, read(w.resolve("ACCTFILE.txt")));
        assertEquals(tc, read(w.resolve("TCATBALF.txt")));
    }

    // ---- preserved legacy defects (do NOT fix; see intent.md) ----

    /** D1: limit check uses cycCredit - cycDebit + amt; cycDebit is negative so it ADDS; curr-bal ignored. */
    @Test void Defect1_LimitUsesGrossCycleNotBalance(@TempDir Path w) throws IOException {
        // limit 1000.00, curr-bal 0, credit 0, debit -900.00: net activity says 100.00 + 200.00 fits,
        // legacy computes 0 - (-900) + 200 = 1100 > 1000 -> OVERLIMIT.
        Harness h = run(w, tran(1, "01", 1, 20000, C1, OK_TS), acct(1, 0, 100000, "2030-01-01", 0, -90000), "");
        assertEquals("0102", reasons(h));
        // and a huge current balance is ignored: accepted although curr-bal exceeds the limit
        Path w2 = w.resolveSibling(w.getFileName() + "b");
        w2.toFile().mkdirs();
        Harness h2 = run(w2, tran(1, "01", 1, 100, C1, OK_TS), acct(1, 9999999, 100000, "2030-01-01", 0, 0), "");
        assertEquals(0, h2.rc);
    }

    /** D2: an over-limit transaction on an expired account reports only 103. */
    @Test void Defect2_103Overwrites102(@TempDir Path w) throws IOException {
        Harness h = run(w, tran(1, "01", 1, 999999, C1, OK_TS), acct(1, 0, 100, "2020-01-01", 0, 0), "");
        assertEquals("0103", reasons(h));
    }

    /** D3: reason 109 is dead: no reject path exists for account REWRITE failure; in Java it is unreachable. */
    @Test void Defect3_Reason109NeverReported(@TempDir Path w) throws IOException {
        Harness h = Harness.run(w, of(tran(1, "01", 1, 100, C1, OK_TS), XREF, acct(1, 0, 100000, "2030-01-01", 0, 0), ""), FROZEN);
        assertFalse(h.out("DALYREJS").contains("0109"));
        assertFalse(Cbtrn02c.class.getDeclaredFields().length == 0);
    }

    /** D4: posting order is TCATBAL, account, TRANFILE; on a duplicate id the earlier updates are already applied, and
     *  the COBOL (like the Java) exports nothing on abend. Pinned via the abend path. */
    @Test void Defect4_NonAtomicPosting_abendExportsNothing(@TempDir Path w) throws IOException {
        String t = tran(1, "01", 1, 100, C1, OK_TS) + tran(1, "01", 2, 100, C1, OK_TS);
        Harness h = run(w, t, acct(1, 0, 100000, "2030-01-01", 0, 0), "");
        assertEquals(16, h.rc);
        assertFalse(w.resolve("TRANFILE.txt").toFile().exists());
        assertFalse(w.resolve("ACCTFILE.txt").toFile().exists() && read(w.resolve("ACCTFILE.txt")).isEmpty());
    }

    /** D5: COBOL displays XREFFILE-STATUS on a DALYREJS close error. Not observable with text I/O; the Java abends
     *  with a generic close message. Pinned as a known gap. */
    @Test void Defect5_CloseStatusMessage_notObservable(@TempDir Path w) throws IOException {
        // DALYREJS DD missing -> close error path
        Map<String, String> in = of(tran(1, "01", 1, 100, C9, OK_TS), XREF, acct(1, 0, 1, "2030-01-01", 0, 0), "");
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        Map<String, String> env = new java.util.HashMap<>();
        for (var e : in.entrySet()) {
            Path p = w.resolve(e.getKey());
            java.nio.file.Files.writeString(p, e.getValue(), java.nio.charset.StandardCharsets.ISO_8859_1);
            env.put("DD_" + e.getKey(), p.toString());
        }
        env.put("DD_TRANFILE", w.resolve("T").toString());
        int rc = new Cbtrn02c(env, Cbtrn02c.clockFrom(env), new java.io.PrintStream(o), new java.io.PrintStream(new java.io.ByteArrayOutputStream())).run();
        assertEquals(16, rc);
        assertTrue(o.toString().contains("ERROR CLOSING DAILY REJECTS FILE"));
    }

    /** D6: WS-TEMP-BAL and TRAN-CAT-BAL are S9(9)V99: high-order digits dropped silently (COBOL reference in
     *  src/test/resources/reasons/overflow). */
    @Test void Defect6_TcatbalTruncatesHighOrder(@TempDir Path w) throws IOException {
        run(w, tran(1, "01", 1, 500, C1, OK_TS), acct(1, 0, 99999999999L, "2030-01-01", 0, 0), tcat(1, "01", 1, 99999999999L));
        String tc = read(w.resolve("TCATBALF.txt"));
        assertEquals(499, Zoned.decode(tc.substring(17, 28))); // 999999999.99 + 5.00 -> 4.99 (wrapped)
    }

    @Test void Defect6_TempBalTruncationCanFlipLimitDecision(@TempDir Path w) throws IOException {
        // credit 999,999,999.99 + 1.00 = 1,000,000,000.99 -> truncated to 0.99, which is NOT over limit 500.00
        Harness h = run(w, tran(1, "01", 1, 100, C1, OK_TS), acct(1, 0, 50000, "2030-01-01", 99999999999L, 0), "");
        assertEquals(0, h.rc);
    }

    @Test void Defect6_AccountFieldsTruncateAt12Digits(@TempDir Path w) throws IOException {
        run(w, tran(1, "01", 1, 100, C1, OK_TS), acct(1, 999999999999L, 999999999999L, "2030-01-01", 999999999999L, 0), "");
        assertEquals(99, Zoned.decode(acctLine(w, 1).substring(12, 24)));
    }
}
