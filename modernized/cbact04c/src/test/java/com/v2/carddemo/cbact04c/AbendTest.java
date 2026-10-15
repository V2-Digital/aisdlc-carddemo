package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    private static final String T = Fixtures.tcat("00000000001", "01", "0001", "0000011648G");
    private static final String X = Fixtures.xref("4111111111111111", "00000000001");
    private static final String A = Fixtures.acct("00000000001", "00000001000{", "");
    private static final String D = Fixtures.disc("DEFAULT", "01", "0001", "00150{");

    private Fixtures.Result run(Path tmp, String t, String x, String a, String d) throws Exception {
        return Fixtures.runMain(tmp.resolve("out"), Fixtures.write(tmp, "t", t), Fixtures.write(tmp, "x", x),
                Fixtures.write(tmp, "a", a), Fixtures.write(tmp, "d", d));
    }

    @Test
    void missingAccount(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = run(tmp, T, X, Fixtures.acct("00000000009", "00000001000{", ""), D);
        assertEquals(16, r.rc());
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000001", "ERROR READING ACCOUNT FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), r.sysout().subList(r.sysout().size() - 4, r.sysout().size()));
    }

    @Test
    void missingXref(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = run(tmp, T, Fixtures.xref("4111111111111111", "00000000009"), A, D);
        assertEquals(16, r.rc());
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000001", "ERROR READING XREF FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), r.sysout().subList(r.sysout().size() - 4, r.sysout().size()));
    }

    @Test
    void missingDefaultRate(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = run(tmp, T, X, A, Fixtures.disc("DEFAULT", "02", "0001", "00150{"));
        assertEquals(16, r.rc());
        assertEquals(List.of("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"),
                r.sysout().subList(r.sysout().size() - 5, r.sysout().size()));
    }

    @Test
    void unreadableDiscgrpUsesLegacyTypoMessage_D5(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = Fixtures.runMain(tmp.resolve("out"), Fixtures.write(tmp, "t", T), Fixtures.write(tmp, "x", X),
                Fixtures.write(tmp, "a", A), tmp.resolve("nope"));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR OPENING DALY REJECTS FILE"));
        assertEquals("ABENDING PROGRAM", r.sysout().get(r.sysout().size() - 1));
    }
}
