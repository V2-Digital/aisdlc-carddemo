package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Expected SYSOUT for the three data abends comes from the real COBOL (legacy/tests/cbact04c-abends.sh). */
class AbendTest {
    @TempDir Path tmp;

    private void abendMatchesCobol(String fixture) throws IOException {
        Path dir = T.ABENDS.resolve(fixture);
        Path out = tmp.resolve("out");
        T.Result r = T.run("2022071800", dir, out);
        assertEquals(16, r.rc());
        assertEquals(Files.readString(dir.resolve("expected-SYSOUT.txt")), r.out());
        assertEquals(Files.readString(dir.resolve("expected-RC.txt")).strip(), "16");
        assertTrue(r.err().contains("CEE3ABD: ABEND U0999"));
        assertFalse(Files.exists(out.resolve("TRANSACT.txt")), "no outputs on abend");
        assertFalse(Files.exists(out.resolve("ACCTFILE.txt")), "no outputs on abend");
    }

    @Test void accountMissing() throws IOException { abendMatchesCobol("account-missing"); }
    @Test void xrefMissing() throws IOException { abendMatchesCobol("xref-missing"); }
    @Test void defaultGroupMissing() throws IOException { abendMatchesCobol("default-missing"); }

    /** Expected text captured from COBOL by pointing one DD at a nonexistent file (status 35). */
    private void missingInput(String dd, String message) throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        for (String f : new String[] {"TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE"}) {
            if (!f.equals(dd)) {
                Files.copy(T.ABENDS.resolve("account-missing").resolve(f + ".txt"), in.resolve(f + ".txt"));
            }
        }
        T.Result r = T.run("2022071800", in, tmp.resolve("out"));
        assertEquals(16, r.rc());
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\n" + message + "\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n",
                r.out().replace("\r", ""));
    }

    @Test void missingTcatbalf() throws IOException { missingInput("TCATBALF", "ERROR OPENING TRANSACTION CATEGORY BALANCE"); }
    @Test void missingXref() throws IOException { missingInput("XREFFILE", "ERROR OPENING CROSS REF FILE35"); }
    /** D4 preserved: the DISCGRP open error says DALY REJECTS FILE. */
    @Test void missingDiscgrpSaysDalyRejectsFile_D4() throws IOException { missingInput("DISCGRP", "ERROR OPENING DALY REJECTS FILE"); }
    @Test void missingAcctfile() throws IOException { missingInput("ACCTFILE", "ERROR OPENING ACCOUNT MASTER FILE"); }
}
