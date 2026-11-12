package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    static final Path GOLDEN = Path.of("../../legacy/golden");
    static final Path ASCII = Path.of("../../app/data/ASCII");

    @TempDir Path tmp;

    private void check(Path acct, Path tcat, String scenario) throws Exception {
        Path tran = tmp.resolve("TRANSACT.txt"), acctOut = tmp.resolve("ACCTFILE.txt");
        ByteArrayOutputStream sys = new ByteArrayOutputStream();
        int rc = new Cbact04c(Map.of(
                "TCATBALF", tcat, "XREFFILE", ASCII.resolve("cardxref.txt"),
                "DISCGRP", ASCII.resolve("discgrp.txt"), "ACCTFILE", acct,
                "ACCTFILE_OUT", acctOut, "TRANSACT", tran),
                "2022071800", Cbact04c.fixedClock("2022071800000000"),
                new PrintStream(sys, true, StandardCharsets.ISO_8859_1)).run();
        Path g = GOLDEN.resolve(scenario);
        assertEquals(Integer.parseInt(Files.readString(g.resolve("RC.txt")).trim()), rc);
        assertArrayEquals(Files.readAllBytes(g.resolve("TRANSACT.txt")), Files.readAllBytes(tran));
        assertArrayEquals(Files.readAllBytes(g.resolve("ACCTFILE.txt")), Files.readAllBytes(acctOut));
        assertArrayEquals(Files.readAllBytes(g.resolve("SYSOUT.txt")), sys.toByteArray());
    }

    @Test
    void stock() throws Exception {
        check(ASCII.resolve("acctdata.txt"), ASCII.resolve("tcatbal.txt"), "CBACT04C");
    }

    @Test
    void afterCbtrn02c() throws Exception {
        Path g = GOLDEN.resolve("CBTRN02C");
        check(g.resolve("ACCTFILE.txt"), g.resolve("TCATBALF.txt"), "CBACT04C-after-CBTRN02C");
    }
}
