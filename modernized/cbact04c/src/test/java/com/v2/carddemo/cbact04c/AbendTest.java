package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    private static final String T = Support.tcat("00000000001", "01", "0001", "0000010000{") + "\n";
    private static final String X = Support.xref("1111222233334444", "00000000001") + "\n";
    private static final String A = Support.acct("00000000001", "00000001000{", "") + "\n";
    private static final String D = Support.disc("DEFAULT", "01", "0001", "00120{") + "\n";

    private void assertAbend(Support.Result r, String... lines) {
        assertEquals(16, r.rc());
        String[] got = r.sysout().split("\n");
        int n = got.length;
        for (int i = 0; i < lines.length; i++) {
            assertEquals(lines[i], got[n - lines.length + i]);
        }
        assertTrue(r.stderr().contains("CEE3ABD: ABEND U0999"));
    }

    @Test
    void missingAccount(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir, T, X, Support.acct("00000000009", "0{", "") + "\n", D);
        assertAbend(Support.run(dir, in, "2022071800"), "ACCOUNT NOT FOUND: 00000000001",
                "ERROR READING ACCOUNT FILE", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM");
    }

    @Test
    void missingXref(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir, T, Support.xref("1111222233334444", "00000000009") + "\n", A, D);
        assertAbend(Support.run(dir, in, "2022071800"), "ACCOUNT NOT FOUND: 00000000001",
                "ERROR READING XREF FILE", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM");
    }

    @Test
    void missingDefaultGroup(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir, T, X, A, Support.disc("DEFAULT", "05", "0001", "00120{") + "\n");
        assertAbend(Support.run(dir, in, "2022071800"), "DISCLOSURE GROUP RECORD MISSING",
                "TRY WITH DEFAULT GROUP CODE", "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023",
                "ABENDING PROGRAM");
    }

    @Test
    void abendWritesNoOutputs(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir, T, X, A, Support.disc("DEFAULT", "05", "0001", "00120{") + "\n");
        Support.run(dir, in, "2022071800");
        assertFalse(java.nio.file.Files.exists(dir.resolve("TRANSACT.txt")));
    }

    @Test
    void discgrpOpenFailureKeepsCopyPasteTextF5(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir, T, X, A, D);
        in.put("DISCGRP", dir); // a directory cannot be read as a file
        Support.Result r = Support.run(dir, in, "2022071800");
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR OPENING DALY REJECTS FILE"));
    }
}
