package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @TempDir Path tmp;

    private Fixture ok() {
        Fixture f = new Fixture(tmp);
        f.acct = Fixture.account("00000000001", "00000001000{", "") + "\n";
        f.tcat = Fixture.tcat("00000000001", "01", "0001", "0000151277{") + "\n";
        f.xref = Fixture.xref("1111222233334444", "00000000001") + "\n";
        f.disc = Fixture.disc("DEFAULT", "01", "0001", "000150").substring(0, 22) + "\n";
        return f;
    }

    private void abends(Fixture f, String text) throws Exception {
        assertEquals(16, f.run());
        assertTrue(f.sysout().contains(text), f.sysout());
        assertTrue(f.sysout().endsWith("ABENDING PROGRAM\n"));
    }

    @Test void okBaseline() throws Exception { assertEquals(0, ok().run()); }

    @Test void missingAccount() throws Exception {
        Fixture f = ok(); f.acct = "";
        abends(f, "ACCOUNT NOT FOUND: 00000000001\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\n");
    }

    @Test void missingXref() throws Exception {
        Fixture f = ok(); f.xref = "";
        abends(f, "ERROR READING XREF FILE\nFILE STATUS IS: NNNN0023\n");
    }

    @Test void nonNumericBalance() throws Exception {
        Fixture f = ok(); f.tcat = Fixture.tcat("00000000001", "01", "0001", "00001X1277{") + "\n";
        assertEquals(16, f.run());
    }

    @Test void missingFile() throws Exception {
        Fixture f = ok();
        f.run();
        java.nio.file.Files.delete(tmp.resolve("DISCGRP.txt"));
        var dds = new java.util.HashMap<String, Path>();
        for (String dd : new String[] {"TCATBALF", "XREFFILE", "ACCTFILE"}) dds.put(dd, tmp.resolve(dd + ".txt"));
        dds.put("DISCGRP", tmp.resolve("DISCGRP.txt"));
        var sys = new java.io.ByteArrayOutputStream();
        int rc = new Cbact04c(dds, "2022071800", f.clock, new java.io.PrintStream(sys)).run();
        assertEquals(16, rc);
        assertTrue(sys.toString().contains("ERROR OPENING DALY REJECTS FILE\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n"));
    }
}
