package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @TempDir Path tmp;

    private Fx.Run run(String acctId, String group, boolean withXref, boolean withDefault, boolean withGroupRow)
            throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Fx.write(in, "TCATBALF", Fx.tcat("00000000001", "01", "0001", "0000011648G"));
        Fx.write(in, "ACCTFILE", Fx.acct(acctId, Fx.BAL0, group));
        Fx.write(in, "XREFFILE", withXref ? Fx.xref("0000000000000001", "00000000001")
                : Fx.xref("0000000000000009", "00000000009"));
        String[] disc = withGroupRow && withDefault
                ? new String[] {Fx.disc("DEFAULT", "01", "0001", "00150{"), Fx.disc("GRPA", "01", "0001", "00300{")}
                : withGroupRow ? new String[] {Fx.disc("GRPA", "01", "0001", "00300{")}
                : withDefault ? new String[] {Fx.disc("DEFAULT", "01", "0001", "00150{")}
                : new String[] {Fx.disc("OTHER", "01", "0001", "00150{")};
        Fx.write(in, "DISCGRP", disc);
        return Fx.run(tmp.resolve("out"), in, tmp.resolve("nostock").toString());
    }

    @Test
    void noDefaultRowAbends() throws IOException {
        Fx.Run r = run("00000000001", "", true, false, false);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"
                + "ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), r.sysout());
    }

    @Test
    void missingAccountAbends() throws IOException {
        Fx.Run r = run("00000000009", "", true, true, false);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: 00000000001\nERROR READING ACCOUNT FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), r.sysout());
    }

    @Test
    void missingXrefAbends() throws IOException {
        Fx.Run r = run("00000000001", "", false, true, false);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: 00000000001\nERROR READING XREF FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), r.sysout());
    }

    @Test
    void missingGroupFallsBackToDefault() throws IOException {
        Fx.Run r = run("00000000001", "NOSUCH", true, true, false);
        assertEquals(0, r.rc());
        assertTrue(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals("0000000145F", r.file("TRANSACT.txt").substring(132, 143)); // DEFAULT 15.00 used
    }

    @Test
    void existingGroupIsUsedWithoutFallback() throws IOException {
        Fx.Run r = run("00000000001", "GRPA", true, true, true);
        assertEquals(0, r.rc());
        assertTrue(!r.sysout().contains("DISCLOSURE GROUP RECORD MISSING"));
        assertEquals("0000000291B", r.file("TRANSACT.txt").substring(132, 143)); // 30.00 rate: 291.21 -> 291.21? see below
    }

    @Test
    void missingInputFileAbends() throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Fx.Run r = Fx.run(tmp.resolve("out"), in, tmp.resolve("nostock").toString());
        assertEquals(16, r.rc());
    }
}
