package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RateTest {
    @TempDir Path tmp;
    final Path ascii = Support.root().resolve("app/data/ASCII");

    private static String tc(String acct, String type, String cat, String bal) {
        return (acct + type + cat + bal).concat(" ".repeat(50 - 28 - (0)));
    }

    private Support.Result run(String[] tcat, String[] disc, String[] xref, boolean useAcct2) throws Exception {
        Path t = Support.write(tmp, "t.txt", tcat);
        Path d = Support.write(tmp, "d.txt", disc);
        Path x = xref == null ? ascii.resolve("cardxref.txt") : Support.write(tmp, "x.txt", xref);
        return Support.run(t, x, d, ascii.resolve("acctdata.txt"), tmp.resolve("o"));
    }

    @Test
    void rateZeroWritesNothing() throws Exception {
        Support.Result r = run(new String[] {"00000000001010001" + "0000010000{", "00000000001020001" + "0000010000{"},
                new String[] {"DEFAULT   010001" + "00150{", "DEFAULT   020001" + "00000{"}, null, false);
        assertEquals(0, r.rc());
        assertEquals(1, Files.readAllLines(r.out().resolve("TRANSACT.txt")).size());
    }

    @Test
    void missingGroupFallsBackToDefault() throws Exception {
        Support.Result r = run(new String[] {"00000000001010001" + "0000010000{"},
                new String[] {"DEFAULT   010001" + "00150{"}, null, false);
        assertEquals(0, r.rc());
        assertTrue(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals("0000000125{", Files.readAllLines(r.out().resolve("TRANSACT.txt")).get(0).substring(132, 143));
    }

    @Test
    void missingDefaultAbends() throws Exception {
        Support.Result r = run(new String[] {"00000000001010001" + "0000010000{"},
                new String[] {"DEFAULT   020001" + "00150{"}, null, false);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith(
                "ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), r.sysout());
    }

    @Test
    void missingAccountAbends() throws Exception {
        Support.Result r = run(new String[] {"99999999999010001" + "0000010000{"},
                new String[] {"DEFAULT   010001" + "00150{"}, null, false);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: 99999999999\nERROR READING ACCOUNT FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), r.sysout());
    }

    @Test
    void missingXrefAbends() throws Exception {
        Support.Result r = run(new String[] {"00000000001010001" + "0000010000{"},
                new String[] {"DEFAULT   010001" + "00150{"}, new String[] {"1111111111111111000000000000000000009"},
                false);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: 00000000001\nERROR READING XREF FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), r.sysout());
    }

    @Test
    void emptyTcatbalLeavesAccountsUnchanged() throws Exception {
        Path t = tmp.resolve("empty.txt");
        Files.writeString(t, "");
        Support.Result r = Support.run(t, ascii.resolve("cardxref.txt"), ascii.resolve("discgrp.txt"),
                ascii.resolve("acctdata.txt"), tmp.resolve("o"));
        assertEquals(0, r.rc());
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n", r.sysout());
        assertEquals(0, Files.size(r.out().resolve("TRANSACT.txt")));
        List<String> in = Files.readAllLines(ascii.resolve("acctdata.txt"));
        assertEquals(in.size(), Files.readAllLines(r.out().resolve("ACCTFILE.txt")).size());
    }
}
