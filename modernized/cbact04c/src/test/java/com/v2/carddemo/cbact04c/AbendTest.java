package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    private static final String T = Fx.pad50("00000000001010001" + "00000100000{".substring(1)) + "\n";

    @Test
    void missingAccount(@TempDir Path in, @TempDir Path out) throws Exception {
        Fx.inputs(in, "ACCTFILE", "", "TCATBALF", T);
        Fx f = Fx.run(in, out);
        assertEquals(16, f.rc);
        assertTrue(f.sysout.endsWith("ACCOUNT NOT FOUND: 00000000001\nERROR READING ACCOUNT FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), f.sysout);
    }

    @Test
    void missingXref(@TempDir Path in, @TempDir Path out) throws Exception {
        Fx.inputs(in, "XREFFILE", "", "TCATBALF", T);
        Fx f = Fx.run(in, out);
        assertEquals(16, f.rc);
        assertTrue(f.sysout.endsWith("ACCOUNT NOT FOUND: 00000000001\nERROR READING XREF FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), f.sysout);
    }

    @Test
    void missingDefaultRate(@TempDir Path in, @TempDir Path out) throws Exception {
        Fx.inputs(in, "DISCGRP", "", "TCATBALF", T);
        Fx f = Fx.run(in, out);
        assertEquals(16, f.rc);
        assertTrue(f.sysout.endsWith("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"
                + "ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), f.sysout);
        assertFalse(java.nio.file.Files.exists(out.resolve("TRANSACT.txt")));
    }
}
