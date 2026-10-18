package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path GOLD = ROOT.resolve("legacy/golden");
    static final Path ASCII = ROOT.resolve("app/data/ASCII");

    @TempDir Path tmp;

    static String read(Path p) throws IOException { return Files.readString(p, StandardCharsets.ISO_8859_1); }

    private void check(String scenario, Path acct, Path tcat) throws IOException {
        Path work = tmp.resolve("acct");
        Files.copy(acct, work);
        Fx fx = new Fx(tmp).run("2022071800", tcat, ASCII.resolve("cardxref.txt"), work, ASCII.resolve("discgrp.txt"));
        Path g = GOLD.resolve(scenario);
        assertEquals(read(g.resolve("TRANSACT.txt")), read(tmp.resolve("tran")), "TRANSACT");
        assertEquals(read(g.resolve("ACCTFILE.txt")), read(work), "ACCTFILE");
        assertEquals(read(g.resolve("SYSOUT.txt")), fx.out, "SYSOUT");
        assertEquals(read(g.resolve("RC.txt")).trim(), String.valueOf(fx.rc), "RC");
        assertEquals(50, fx.tran().size());
        assertEquals(50, fx.acct().size());
    }

    @Test void stock() throws IOException {
        check("CBACT04C", ASCII.resolve("acctdata.txt"), ASCII.resolve("tcatbal.txt"));
    }

    @Test void afterCbtrn02c() throws IOException {
        Path g = GOLD.resolve("CBTRN02C");
        check("CBACT04C-after-CBTRN02C", g.resolve("ACCTFILE.txt"), g.resolve("TCATBALF.txt"));
    }

    /** AC3 / D1: last account's master record is untouched although its transaction is written. */
    @Test void defectD1_lastAccountNotRewritten() throws IOException {
        Path g = GOLD.resolve("CBTRN02C");
        Path work = tmp.resolve("acct");
        Files.copy(g.resolve("ACCTFILE.txt"), work);
        Fx fx = new Fx(tmp).run("2022071800", g.resolve("TCATBALF.txt"), ASCII.resolve("cardxref.txt"), work, ASCII.resolve("discgrp.txt"));
        String before = Files.readAllLines(g.resolve("ACCTFILE.txt"), StandardCharsets.ISO_8859_1).stream()
                .filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        String after = fx.acct().stream().filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        assertEquals(before, after);
        String tx = fx.tran().stream().filter(l -> l.contains("Int. for a/c 00000000050")).findFirst().orElseThrow();
        assertEquals("0000000187G", tx.substring(132, 143));
    }
}
