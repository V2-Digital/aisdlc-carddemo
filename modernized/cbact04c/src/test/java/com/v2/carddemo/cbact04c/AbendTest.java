package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Java abends must match the real COBOL run captured by legacy/tests/cbact04c-abends.sh. */
class AbendTest {
    private static final Path CASES = Fixture.ROOT.resolve("legacy/tests/cbact04c-abends");

    private void check(String name, Path tmp) throws IOException {
        Path c = CASES.resolve(name);
        Fixture f = new Fixture(tmp);
        f.stock(null);   // stock inputs, then the same overrides run.sh's INPUT_DIR applies
        for (String dd : new String[] {"TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE"}) {
            if (Files.exists(c.resolve(dd + ".txt"))) {
                Files.copy(c.resolve(dd + ".txt"), f.file(dd), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        String acctBefore = f.read("ACCTFILE");
        f.run();
        assertEquals(16, f.rc);
        assertEquals(Fixture.slurp(c.resolve("SYSOUT.expected")), f.sysout);
        assertEquals("", f.read("TRANSACT"), "no transactions written before the abend");
        assertEquals(acctBefore.replace("\r", ""), f.read("ACCTFILE"), "no account rewritten");
    }

    @Test void missingAccount(@TempDir Path t) throws IOException { check("noacct", t); }
    @Test void missingXref(@TempDir Path t) throws IOException { check("noxref", t); }
    @Test void missingDefaultRate(@TempDir Path t) throws IOException { check("nodefault", t); }

    @Test void openFailureSetsRc16(@TempDir Path t) {
        Fixture f = new Fixture(t);   // no input files exist
        f.run();
        assertEquals(16, f.rc);
        assertTrue(f.sysout.contains("ERROR OPENING TRANSACTION CATEGORY BALANCE"));
        assertTrue(f.sysout.endsWith("FILE STATUS IS: NNNN0035\nABENDING PROGRAM\n"));
    }
}
