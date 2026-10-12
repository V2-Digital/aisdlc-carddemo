package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ErrorPathTest {
    static final Path IN = DifferentialEdgeTest.EDGE.resolve("in");
    static final String ABEND = "CEE3ABD: ABEND U0999 TIMING=0";

    private Result run(Path dir, Path tcat, Path xref, Path disc) throws IOException {
        return TestSupport.run(dir, "2022071800", CLOCK, tcat, xref, disc, copy(IN.resolve("ACCTFILE.txt"), dir, "ACCTFILE.txt"));
    }

    private Path file(Path dir, String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }

    private static final String T3 = "00000000003" + "010001" + "0000023399G" + " ".repeat(22) + "\n";

    @Test
    void E1_missingInputFilesUseLegacyMessages(@TempDir Path d) throws IOException {
        Path no = d.resolve("nope");
        Result r = run(d, no, IN.resolve("XREFFILE.txt"), IN.resolve("DISCGRP.txt"));
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nERROR OPENING TRANSACTION CATEGORY BALANCE\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n", r.sysout());
        assertEquals(16, r.rc());
        assertTrue(r.stderr().contains(ABEND));
        assertTrue(run(d, IN.resolve("TCATBALF.txt"), no, IN.resolve("DISCGRP.txt")).sysout().contains("ERROR OPENING CROSS REF FILE35\n"));
        assertTrue(run(d, IN.resolve("TCATBALF.txt"), IN.resolve("XREFFILE.txt"), no).sysout().contains("ERROR OPENING DALY REJECTS FILE\n")); // F1
    }

    @Test
    void E3_accountNotFound(@TempDir Path d) throws IOException {
        Path t = file(d, "t.txt", "99999999999" + "010001" + "0000000100{" + " ".repeat(22) + "\n");
        Result r = run(d, t, IN.resolve("XREFFILE.txt"), IN.resolve("DISCGRP.txt"));
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: 99999999999\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
        assertEquals(16, r.rc());
    }

    @Test
    void E4_xrefNotFound(@TempDir Path d) throws IOException {
        Path x = file(d, "x.txt", "");
        Result r = run(d, file(d, "t.txt", T3), x, IN.resolve("DISCGRP.txt"));
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: 00000000003\nERROR READING XREF FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
        assertEquals(16, r.rc());
    }

    @Test
    void E5_defaultGroupMissing(@TempDir Path d) throws IOException {
        Result r = run(d, file(d, "t.txt", T3), IN.resolve("XREFFILE.txt"), file(d, "dg.txt", ""));
        assertTrue(r.sysout().endsWith("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\nERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
        assertEquals(16, r.rc());
    }

    @Test
    void E2_transactCannotBeCreated(@TempDir Path d) throws IOException {
        Result r = TestSupport.run(d.resolve("missing-dir"), "2022071800", CLOCK, IN.resolve("TCATBALF.txt"),
                IN.resolve("XREFFILE.txt"), IN.resolve("DISCGRP.txt"), copy(IN.resolve("ACCTFILE.txt"), d, "ACCTFILE.txt"));
        assertTrue(r.sysout().endsWith("ERROR OPENING TRANSACTION FILE\nFILE STATUS IS: NNNN0030\nABENDING PROGRAM\n"));
        assertEquals(16, r.rc());
    }

    @Test
    void E7_emptyTcatbal(@TempDir Path d) throws IOException {
        Result r = run(d, file(d, "t.txt", ""), IN.resolve("XREFFILE.txt"), IN.resolve("DISCGRP.txt"));
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n", r.sysout());
        assertEquals(0, r.rc());
        assertEquals(0, Files.size(d.resolve("TRANSACT.txt")));
        assertEquals(Files.readString(IN.resolve("ACCTFILE.txt")), Files.readString(d.resolve("ACCTFILE.txt")));
    }

    @Test
    void E9_nonNumericBalanceAbends(@TempDir Path d) throws IOException {
        Result r = run(d, file(d, "t.txt", "00000000003" + "010001" + "00000XX399G" + " ".repeat(22) + "\n"), IN.resolve("XREFFILE.txt"), IN.resolve("DISCGRP.txt"));
        assertTrue(r.sysout().contains("FILE STATUS IS: NNNN0034"));
        assertEquals(16, r.rc());
    }

    @Test
    void E12_duplicateKeyIsLoadErrorExit3(@TempDir Path d) throws IOException {
        String line = IN.resolve("TCATBALF.txt").toFile().exists() ? T3 : T3;
        Result r = run(d, file(d, "t.txt", line + line), IN.resolve("XREFFILE.txt"), IN.resolve("DISCGRP.txt"));
        assertEquals(3, r.rc());
        assertTrue(r.stderr().contains("LOAD ERROR: duplicate key"));
    }

    @Test
    void usageErrorExit2() {
        assertEquals(2, Main.run(new String[] {"--parm", "x"}, System.out, new java.io.PrintStream(new java.io.ByteArrayOutputStream())));
    }
}
