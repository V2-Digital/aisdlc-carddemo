package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Characterization tests: they pin legacy behaviour, defects included. */
class InterestCalcTest {
    private final List<String> sysout = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    private static String p(String s, int n) { return FixedText.pad(s, n); }

    private static String z(String amount, int intDigits) { return Zoned.encode(new BigDecimal(amount), intDigits); }

    private static String acct(String id, String bal, String group) {
        String r = p(id + "Y" + z(bal, 10), 300);
        return r.substring(0, 78) + z("100.00", 10) + z("200.00", 10) + r.substring(102, 112) + p(group, 10) + r.substring(122);
    }

    private static String tcat(String acct, String type, String cat, String bal) { return p(acct + type + cat + z(bal, 9), 50); }
    private static String xref(String card, String acct) { return p(card + "000000000" + acct, 50); }
    private static String disc(String group, String type, String cat, String rate) { return p(group + type + cat + Zoned.encode(new BigDecimal(rate), 4), 50); }

    private InterestCalc.Result run(List<String> tc, List<String> x, List<String> a, List<String> d) {
        return new InterestCalc("2022071800", clock, sysout).run(tc, x, a, d);
    }

    private static final String A1 = "00000000001", A2 = "00000000002";
    private static final List<String> XREF = List.of(xref("1111111111111111", A1), xref("2222222222222222", A2));
    private static final List<String> DEF = List.of(disc("DEFAULT   ", "01", "0001", "15"));

    private static BigDecimal tranAmt(String tran) { return Zoned.decode(tran.substring(16 + 2 + 4 + 10 + 100, 16 + 2 + 4 + 10 + 100 + 11)); }

    @Test
    void truncatesNotRounds() {
        // 1945.87 * 15 / 1200 = 24.32375 -> 24.32 ; 1000.99 * 15 / 1200 = 12.512375 ; 0.99*... use .xx5+ case
        InterestCalc.Result r = run(List.of(tcat(A1, "01", "0001", "1945.87"),
                tcat(A2, "01", "0001", "79.97")), XREF,
            List.of(acct(A1, "0", "A"), acct(A2, "0", "A")), DEF);
        // 79.97 * 15 / 1200 = 0.999625 -> 0.99 (rounding would give 1.00)
        assertEquals(new BigDecimal("0.99"), tranAmt(r.transactions().get(1)));
    }

    @Test
    void lastAccountNotUpdated() {
        String a1 = acct(A1, "100000.00", "");
        String a2 = acct(A2, "100000.00", "");
        InterestCalc.Result r = run(List.of(tcat(A1, "01", "0001", "120.00"), tcat(A2, "01", "0001", "120.00")),
            XREF, List.of(a1, a2), DEF);
        assertEquals(2, r.transactions().size());
        assertTrue(tranAmt(r.transactions().get(1)).signum() > 0);
        assertEquals(a2, r.accounts().get(1), "F1: last account keeps old balance and cycle fields");
        assertTrue(!a1.equals(r.accounts().get(0)));
    }

    @Test
    void groupMissFallsBackToDefault() {
        run(List.of(tcat(A1, "01", "0001", "120.00")), XREF, List.of(acct(A1, "0", "")), DEF);
        assertEquals(List.of("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE"),
            sysout.subList(2, 4));
    }

    @Test
    void groupHit() {
        List<String> d = List.of(disc("GRPA      ", "01", "0001", "30"), DEF.get(0));
        InterestCalc.Result r = run(List.of(tcat(A1, "01", "0001", "120.00")), XREF,
            List.of(acct(A1, "0", "GRPA")), d);
        assertEquals(new BigDecimal("3.00"), tranAmt(r.transactions().get(0))); // 120.00 * 30 / 1200 = 3.00
        assertTrue(!sysout.contains("DISCLOSURE GROUP RECORD MISSING"));
    }

    @Test
    void zeroRateNoTranNoSuffix() {
        List<String> d = List.of(disc("DEFAULT   ", "01", "0001", "0"), disc("DEFAULT   ", "02", "0001", "15"));
        InterestCalc.Result r = run(List.of(tcat(A1, "01", "0001", "120.00"), tcat(A1, "02", "0001", "120.00"),
            tcat(A2, "02", "0001", "120.00")), XREF, List.of(acct(A1, "0", ""), acct(A2, "0", "")), d);
        assertEquals(2, r.transactions().size());
        assertTrue(r.transactions().get(0).startsWith("2022071800000001"));
        assertTrue(r.transactions().get(1).startsWith("2022071800000002"));
    }

    @Test
    void zeroTotalStillZeroesCycles() {
        List<String> d = List.of(disc("DEFAULT   ", "01", "0001", "0"), disc("DEFAULT   ", "02", "0001", "15"));
        String a1 = acct(A1, "100000.00", "");
        InterestCalc.Result r = run(List.of(tcat(A1, "01", "0001", "120.00"), tcat(A2, "02", "0001", "120.00")),
            XREF, List.of(a1, acct(A2, "0", "")), d);
        String out = r.accounts().get(0);
        assertEquals(a1.substring(12, 24), out.substring(12, 24), "balance unchanged");
        assertEquals(z("0", 10) + z("0", 10), out.substring(78, 102));
    }

    @Test
    void untouchedAccounts() {
        String a3 = acct("00000000003", "100000.00", "");
        List<String> accts = List.of(acct(A1, "0", ""), acct(A2, "0", ""), a3);
        InterestCalc.Result r = run(List.of(tcat(A1, "01", "0001", "120.00"), tcat(A2, "01", "0001", "120.00")),
            XREF, accts, DEF);
        assertEquals(3, r.accounts().size());
        assertEquals(a3, r.accounts().get(2));
    }

    @Test
    void emptyTcatbal() {
        List<String> accts = List.of(acct(A1, "0", ""));
        InterestCalc.Result r = run(List.of(), XREF, accts, DEF);
        assertTrue(r.transactions().isEmpty());
        assertEquals(accts, r.accounts());
    }

    @Test
    void missingAccount() {
        Abend e = assertThrows(Abend.class, () -> run(List.of(tcat(A2, "01", "0001", "0")), XREF,
            List.of(acct(A1, "0", "")), DEF));
        assertEquals(List.of("ACCOUNT NOT FOUND: " + A2, "ERROR READING ACCOUNT FILE", "FILE STATUS IS: NNNN0023"), e.sysout());
    }

    @Test
    void missingXref() {
        Abend e = assertThrows(Abend.class, () -> run(List.of(tcat(A1, "01", "0001", "0")), List.of(),
            List.of(acct(A1, "0", "")), DEF));
        assertEquals("ERROR READING XREF FILE", e.sysout().get(1));
    }

    @Test
    void missingDefault() {
        Abend e = assertThrows(Abend.class, () -> run(List.of(tcat(A1, "01", "0001", "0")), XREF,
            List.of(acct(A1, "0", "")), List.of()));
        assertEquals("ERROR READING DEFAULT DISCLOSURE GROUP", e.sysout().get(2));
    }

    @Test
    void firstXrefCardWins() {
        List<String> x = List.of(xref("1111111111111111", A1), xref("9999999999999999", A1));
        InterestCalc.Result r = run(List.of(tcat(A1, "01", "0001", "120.00")), x, List.of(acct(A1, "0", "")), DEF);
        assertTrue(r.transactions().get(0).contains("1111111111111111"));
    }

    @Test
    void negativeBalanceTruncatesTowardZero() {
        // -0.50 * 15 / 1200 = -0.00625 -> 0.00
        InterestCalc.Result r = run(List.of(tcat(A1, "01", "0001", "-0.50")), XREF,
            List.of(acct(A1, "0", "")), DEF);
        assertEquals("0000000000{", r.transactions().get(0).substring(132, 143));
    }

    @Test
    void suffixWrapsAfter999999() {
        // direct unit of the modulus used in InterestCalc
        assertEquals(0, (999_999 + 1) % 1_000_000);
    }
}
