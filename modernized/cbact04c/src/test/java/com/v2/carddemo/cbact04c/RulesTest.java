package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesTest {
    @TempDir
    Path tmp;

    private static final String CARD = "1111222233334444";

    private Fx base(List<String> tcat, List<String> accts, List<String> disc) throws Exception {
        return new Fx(tmp).file("TCATBALF", tcat)
                .file("XREFFILE", List.of(Fx.xref(CARD, 1), Fx.xref("5555666677778888", 2), Fx.xref("9999000011112222", 3)))
                .file("ACCTFILE", accts).file("DISCGRP", disc);
    }

    @Test
    void zeroRateWritesNoTransactionButZeroesCycles() throws Exception {
        Fx f = base(List.of(Fx.tcat(1, "01", "0001", "00000010000"), Fx.tcat(2, "01", "0001", "00000010000")),
                List.of(Fx.acct(1, "00000005000{", "00000001234A", "00000004321B", ""),
                        Fx.acct(2, "00000005000{", "00000001234A", "00000004321B", "")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "00000{"))).run("2022071800");
        assertEquals(0, f.rc);
        assertEquals(0, f.outRecs("TRANSACT", 350).size());
        List<String> a = f.outRecs("ACCTFILE", 300);
        assertEquals("00000000000{", Records.field(a.get(0), 79, 12));
        assertEquals("00000000000{", Records.field(a.get(0), 91, 12));
        assertEquals("00000005000{", Records.field(a.get(0), 13, 12));
        // last account (2) is not rewritten (D1)
        assertEquals("00000001234A", Records.field(a.get(1), 79, 12));
    }

    @Test
    void groupHitUsesGroupRateAndPrintsNoMissingLines() throws Exception {
        Fx f = base(List.of(Fx.tcat(1, "01", "0001", "00000120000"), Fx.tcat(2, "01", "0001", "0000000000{")),
                List.of(Fx.acct(1, Fx.Z12, Fx.Z12, Fx.Z12, "GRPA"), Fx.acct(2, Fx.Z12, Fx.Z12, Fx.Z12, "GRPA")),
                List.of(Fx.disc("GRPA", "01", "0001", "00030{"), Fx.disc("DEFAULT", "01", "0001", "00120{")))
                .run("2022071800");
        assertEquals(0, f.rc);
        assertFalse(f.sysout.contains("DISCLOSURE GROUP RECORD MISSING"));
        // 1200.00 * 3.00 / 1200 = 3.00 (the DEFAULT rate would give 12.00)
        assertEquals("0000000030{", Records.field(f.outRecs("TRANSACT", 350).get(0), 133, 11));
    }

    @Test
    void missingGroupFallsBackToDefault() throws Exception {
        Fx f = base(List.of(Fx.tcat(1, "01", "0001", "00000120000"), Fx.tcat(2, "01", "0001", "0000000000{")),
                List.of(Fx.acct(1, Fx.Z12, Fx.Z12, Fx.Z12, "NOPE"), Fx.acct(2, Fx.Z12, Fx.Z12, Fx.Z12, "")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "00120{"))).run("2022071800");
        assertEquals(0, f.rc);
        assertTrue(f.sysout.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals("0000000120{", Records.field(f.outRecs("TRANSACT", 350).get(0), 133, 11));
    }

    private void assertAbend(Fx f, String tail) {
        assertEquals(16, f.rc);
        assertTrue(f.sysout.endsWith(tail + "ABENDING PROGRAM\n"), f.sysout);
        assertFalse(f.sysout.contains("END OF EXECUTION"));
    }

    @Test
    void missingDefaultAbends() throws Exception {
        Fx f = base(List.of(Fx.tcat(1, "01", "0001", "00000120000")), List.of(Fx.acct(1, Fx.Z12, Fx.Z12, Fx.Z12, "")),
                List.of(Fx.disc("OTHER", "01", "0001", "00120{"))).run("2022071800");
        assertAbend(f, "DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"
                + "ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\n");
    }

    @Test
    void missingAccountAbends() throws Exception {
        Fx f = base(List.of(Fx.tcat(7, "01", "0001", "0000000000{")), List.of(Fx.acct(1, Fx.Z12, Fx.Z12, Fx.Z12, "")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "00120{"))).run("2022071800");
        assertAbend(f, "ACCOUNT NOT FOUND: 00000000007\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\n");
    }

    @Test
    void missingXrefAbends() throws Exception {
        Fx f = base(List.of(Fx.tcat(7, "01", "0001", "0000000000{")), List.of(Fx.acct(7, Fx.Z12, Fx.Z12, Fx.Z12, "")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "00120{"))).run("2022071800");
        assertAbend(f, "ACCOUNT NOT FOUND: 00000000007\nERROR READING XREF FILE\nFILE STATUS IS: NNNN0023\n");
    }

    @Test
    void severalCategoriesAccumulateInOneAccountRewrite() throws Exception {
        Fx f = base(List.of(Fx.tcat(1, "01", "0001", "00000120000"), Fx.tcat(1, "01", "0002", "00000120000"),
                        Fx.tcat(2, "01", "0001", "0000000000{")),
                List.of(Fx.acct(1, "00000000100{", Fx.Z12, Fx.Z12, ""), Fx.acct(2, Fx.Z12, Fx.Z12, Fx.Z12, "")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "00010{"), Fx.disc("DEFAULT", "01", "0002", "00020{")))
                .run("2022071800");
        assertEquals(3, f.outRecs("TRANSACT", 350).size()); // non-zero rate writes even for a zero balance
        // 1.00 + 2.00 = 3.00 added to 10.00
        assertEquals("00000000130{", Records.field(f.outRecs("ACCTFILE", 300).get(0), 13, 12));
    }
}
