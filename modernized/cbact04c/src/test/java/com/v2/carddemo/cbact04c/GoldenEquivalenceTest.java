package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.TestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @Test
    void stock(@TempDir Path dir) throws IOException {
        Path g = GOLDEN.resolve("CBACT04C");
        Path acct = copy(SAMPLE.resolve("acctdata.txt"), dir, "ACCTFILE.txt");
        Result r = TestSupport.run(dir, "2022071800", CLOCK, SAMPLE.resolve("tcatbal.txt"),
                SAMPLE.resolve("cardxref.txt"), SAMPLE.resolve("discgrp.txt"), acct);
        check(g, dir, r, 50, 50, 152);
    }

    @Test
    void afterCbtrn02c(@TempDir Path dir) throws IOException {
        Path g = GOLDEN.resolve("CBACT04C-after-CBTRN02C");
        Path in = GOLDEN.resolve("CBTRN02C");
        Path acct = copy(in.resolve("ACCTFILE.txt"), dir, "ACCTFILE.txt");
        Result r = TestSupport.run(dir, "2022071800", CLOCK, in.resolve("TCATBALF.txt"),
                SAMPLE.resolve("cardxref.txt"), SAMPLE.resolve("discgrp.txt"), acct);
        check(g, dir, r, 50, 50, 302);
        // D1: last account (50) is byte-identical to its input
        var in50 = Files.readAllLines(in.resolve("ACCTFILE.txt")).get(49);
        assertEquals(in50, Files.readAllLines(dir.resolve("ACCTFILE.txt")).get(49));
    }

    private void check(Path g, Path dir, Result r, int tx, int ac, int sys) throws IOException {
        assertBytes(g.resolve("TRANSACT.txt"), dir.resolve("TRANSACT.txt"), "TRANSACT");
        assertBytes(g.resolve("ACCTFILE.txt"), dir.resolve("ACCTFILE.txt"), "ACCTFILE");
        assertBytes(Files.readAllBytes(g.resolve("SYSOUT.txt")), r.sysout().getBytes(StandardCharsets.ISO_8859_1), "SYSOUT");
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(r.rc()));
        assertEquals(tx, lines(dir.resolve("TRANSACT.txt")));
        assertEquals(ac, lines(dir.resolve("ACCTFILE.txt")));
        assertEquals(sys, r.sysout().split("\n").length);
    }
}
