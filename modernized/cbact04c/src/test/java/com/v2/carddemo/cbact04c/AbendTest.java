package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @TempDir Path tmp;

    private Fx base() {
        Fx f = new Fx(tmp).rate("12.00");
        f.tcat.add(Fx.tcatRec("00000000001", "01", "0001", "100.00"));
        f.xref.add(Fx.xrefRec("4111111111111111", "00000000001"));
        f.acct.add(Fx.acctRec("00000000001", "100.00", ""));
        return f;
    }

    private void assertAbend(Fx f, String tail) throws IOException {
        assertEquals(16, f.rc);
        assertTrue(f.sysout.endsWith(tail + "ABENDING PROGRAM\n"), f.sysout);
        assertTrue(f.stderr.contains("CEE3ABD: ABEND U0999"));
        assertNull(f.lines("TRANSACT.out"));
        assertNull(f.lines("ACCTFILE.out"));
    }

    @Test void missingAccount() throws IOException {
        Fx f = base();
        f.acct.clear();
        assertAbend(f.run(), "ACCOUNT NOT FOUND: 00000000001\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\n");
    }

    @Test void missingXref() throws IOException {
        Fx f = base();
        f.xref.clear();
        assertAbend(f.run(), "ACCOUNT NOT FOUND: 00000000001\nERROR READING XREF FILE\nFILE STATUS IS: NNNN0023\n");
    }

    @Test void missingDefaultDisclosureRow() throws IOException {
        Fx f = base();
        f.disc.clear();
        assertAbend(f.run(), "DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"
                + "ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\n");
    }

    @Test void abendMidRun_leavesInPlaceAcctfileUntouched() throws IOException {
        Fx f = base();
        f.tcat.add(Fx.tcatRec("00000000002", "01", "0001", "1.00")); // account 2 missing
        f.write("TCATBALF", f.tcat); f.write("XREFFILE", f.xref); f.write("DISCGRP", f.disc); f.write("ACCTFILE", f.acct);
        byte[] before = Files.readAllBytes(tmp.resolve("ACCTFILE.txt"));
        var o = new java.io.PrintStream(java.io.OutputStream.nullOutputStream());
        int rc = Main.run(new String[] {"--PARM=2022071800", "--TCATBALF=" + tmp.resolve("TCATBALF.txt"),
                "--XREFFILE=" + tmp.resolve("XREFFILE.txt"), "--DISCGRP=" + tmp.resolve("DISCGRP.txt"),
                "--ACCTFILE=" + tmp.resolve("ACCTFILE.txt"), "--TRANSACT=" + tmp.resolve("T")}, o, o);
        assertEquals(16, rc);
        assertArrayEquals(before, Files.readAllBytes(tmp.resolve("ACCTFILE.txt")));
        assertFalse(Files.exists(tmp.resolve("T")));
    }

    @Test void inputErrors_exit16() throws IOException {
        Fx dup = base();
        dup.acct.add(dup.acct.get(0));
        Fx d = dup.run();
        assertEquals(16, d.rc);
        assertTrue(d.sysout.startsWith("INPUT ERROR: ACCTFILE duplicate key 00000000001"));
    }
}
