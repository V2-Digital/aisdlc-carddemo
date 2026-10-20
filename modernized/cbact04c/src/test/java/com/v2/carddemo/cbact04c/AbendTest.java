package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    private static final List<String> TCAT = List.of(RulesTest.tcat(1, "01", "0000010000{"),
            RulesTest.tcat(2, "01", "0000010000{"));

    private static void assertAbend(Fx fx, String... expectedLines) throws IOException {
        assertEquals(16, fx.rc);
        assertEquals("16\n", fx.text("RC"));
        List<String> sys = fx.lines("SYSOUT");
        assertEquals("ABENDING PROGRAM", sys.get(sys.size() - 1));
        for (String e : expectedLines) {
            assertTrue(sys.contains(e), "SYSOUT lacks: " + e);
        }
    }

    @Test
    void missingAccount(@TempDir Path tmp) throws IOException {
        Fx fx = RulesTest.run(tmp, TCAT, List.of(RulesTest.acct(2, "00000001000{", "")),
                List.of(RulesTest.xref(1), RulesTest.xref(2)), RulesTest.DISC);
        assertAbend(fx, "ACCOUNT NOT FOUND: 00000000001", "ERROR READING ACCOUNT FILE", "FILE STATUS IS: NNNN0023");
        assertEquals(0, fx.lines("TRANSACT").size());
    }

    @Test
    void missingXref(@TempDir Path tmp) throws IOException {
        Fx fx = RulesTest.run(tmp, TCAT, List.of(RulesTest.acct(1, "00000001000{", ""), RulesTest.acct(2, "00000001000{", "")),
                List.of(RulesTest.xref(1)), RulesTest.DISC);
        assertAbend(fx, "ACCOUNT NOT FOUND: 00000000002", "ERROR READING XREF FILE", "FILE STATUS IS: NNNN0023");
        assertEquals(1, fx.lines("TRANSACT").size()); // D5: work done before the failure is kept
    }

    @Test
    void missingDefaultRow(@TempDir Path tmp) throws IOException {
        Fx fx = RulesTest.run(tmp, TCAT, List.of(RulesTest.acct(1, "00000001000{", ""), RulesTest.acct(2, "00000001000{", "")),
                List.of(RulesTest.xref(1), RulesTest.xref(2)), List.of(Fx.disc("GRP1", "01", "0001", "00300{")));
        assertAbend(fx, "DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023");
    }

    @Test
    void missingInputFiles(@TempDir Path tmp) throws IOException {
        for (String dd : new String[] {"TCATBALF", "XREFFILE", "ACCTFILE", "DISCGRP"}) {
            Path t = tmp.resolve(dd);
            Files.createDirectories(t);
            Fx fx = RulesTest.run(t, TCAT, List.of(RulesTest.acct(1, "00000001000{", "")), List.of(RulesTest.xref(1)),
                    RulesTest.DISC);
            Files.delete(fx.in.get(dd));
            fx.run();
            assertAbend(fx, "FILE STATUS IS: NNNN0035");
        }
    }
}
