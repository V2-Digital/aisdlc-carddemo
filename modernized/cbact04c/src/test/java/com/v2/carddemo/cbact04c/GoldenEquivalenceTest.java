package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    private static final Path ROOT = Support.repoRoot();
    private static final Path ASCII = ROOT.resolve("app/data/ASCII");

    private static void stage(Path in, Path acct, Path tcat) throws IOException {
        Files.createDirectories(in);
        Files.copy(tcat, in.resolve("TCATBALF.txt"));
        Files.copy(acct, in.resolve("ACCTFILE.txt"));
        Files.copy(ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
    }

    private static void compare(Support.Result r, Path golden, int tran, int acct, int sysoutLines) throws IOException {
        assertEquals(Files.readString(golden.resolve("RC.txt")).trim(), String.valueOf(r.rc()));
        for (String f : List.of("TRANSACT.txt", "ACCTFILE.txt")) {
            assertArrayEquals(Files.readAllBytes(golden.resolve(f)), Files.readAllBytes(r.out().resolve(f)), f);
        }
        assertArrayEquals(Files.readAllBytes(golden.resolve("SYSOUT.txt")), r.sysout().getBytes("ISO-8859-1"), "SYSOUT");
        assertEquals(tran, Files.readAllLines(r.out().resolve("TRANSACT.txt")).size());
        assertEquals(acct, Files.readAllLines(r.out().resolve("ACCTFILE.txt")).size());
        assertEquals(sysoutLines, Files.readAllLines(golden.resolve("SYSOUT.txt")).size());
    }

    @Test
    void stock(@TempDir Path tmp) throws IOException {
        stage(tmp.resolve("in"), ASCII.resolve("acctdata.txt"), ASCII.resolve("tcatbal.txt"));
        compare(Support.run(tmp.resolve("in"), tmp.resolve("out")), ROOT.resolve("legacy/golden/CBACT04C"), 50, 50, 152);
    }

    @Test
    void afterCbtrn02c(@TempDir Path tmp) throws IOException {
        Path g = ROOT.resolve("legacy/golden/CBTRN02C");
        stage(tmp.resolve("in"), g.resolve("ACCTFILE.txt"), g.resolve("TCATBALF.txt"));
        compare(Support.run(tmp.resolve("in"), tmp.resolve("out")),
                ROOT.resolve("legacy/golden/CBACT04C-after-CBTRN02C"), 50, 50, 302);
    }
}
