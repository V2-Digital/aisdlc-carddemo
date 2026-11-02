package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ErrorPathTest {
    @TempDir Path tmp;

    private static final String A1 = "00000000001";
    private static final String A2 = "00000000002";

    private Fx.Run go(List<String> tcat, List<String> xref, List<String> disc, List<String> acct)
            throws IOException {
        return Fx.run(Fx.dir(tmp, "in", tcat, xref, disc, acct), tmp.resolve("out"));
    }

    private static final List<String> TC = List.of(Fx.tcat(A1, "01", "0001", "100"), Fx.tcat(A2, "01", "0001", "100"));
    private static final List<String> XR = List.of(Fx.xref("1111111111111111", A1), Fx.xref("2222222222222222", A2));
    private static final List<String> DS = List.of(Fx.disc("DEFAULT", "01", "0001", "15"));
    private static final List<String> AC = List.of(Fx.acct(A1, "0", "0", "0", ""), Fx.acct(A2, "0", "0", "0", ""));

    @Test
    void missingGroupFallsBackToDefault() throws IOException {
        Fx.Run r = go(TC, XR, List.of(Fx.disc("DEFAULT", "01", "0001", "15"),
                Fx.disc("GOLD", "01", "0001", "30")), List.of(Fx.acct(A1, "0", "0", "0", "GOLD"),
                Fx.acct(A2, "0", "0", "0", "NOPE")));
        assertEquals(0, r.rc());
        assertTrue(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        // real group hit 30%: 100*30/1200 = 2.50 ; fallback 15%: 1.25
        assertEquals(Zoned.encode(new java.math.BigDecimal("2.50"), 11, 2), r.lines("TRANSACT").get(0).substring(132, 143));
        assertEquals(Zoned.encode(new java.math.BigDecimal("1.25"), 11, 2), r.lines("TRANSACT").get(1).substring(132, 143));
    }

    @Test
    void missingAccountAbends() throws IOException {
        Fx.Run r = go(TC, XR, DS, List.of(Fx.acct(A1, "0", "0", "0", "")));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ACCOUNT NOT FOUND: " + A2 + "\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
        assertEquals(1, r.lines("TRANSACT").size(), "no records after the abend");
        assertFalse(r.sysout().contains("END OF EXECUTION"));
    }

    @Test
    void missingXrefAbends() throws IOException {
        Fx.Run r = go(TC, List.of(XR.get(0)), DS, AC);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ACCOUNT NOT FOUND: " + A2 + "\nERROR READING XREF FILE\n"));
        assertEquals(1, r.lines("TRANSACT").size());
    }

    @Test
    void missingDefaultAbends() throws IOException {
        Fx.Run r = go(TC, XR, List.of(Fx.disc("DEFAULT", "05", "0001", "15")), AC);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("TRY WITH DEFAULT GROUP CODE\nERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
        assertEquals(0, r.lines("TRANSACT").size());
    }

    @Test
    void emptyTcatbalCopiesAccountsUnchanged() throws IOException {
        Fx.Run r = go(List.of(), XR, DS, AC);
        assertEquals(0, r.rc());
        assertEquals(0, r.lines("TRANSACT").size());
        assertEquals(AC, r.lines("ACCTFILE"));
    }

    @Test
    void clockDefaultsToSystemTime() {
        String t = Main.systemClock();
        assertEquals(16, t.length());
        assertTrue(t.startsWith(String.valueOf(java.time.Year.now(java.time.ZoneOffset.UTC).getValue()))
                || t.startsWith(String.valueOf(java.time.Year.now(java.time.ZoneOffset.UTC).getValue() - 1)));
    }
}
