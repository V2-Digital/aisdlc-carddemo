package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path dir;

    private void check(String scenario, Path acct, Path tcat) throws Exception {
        Path root = Fx.repo();
        Path a = root.resolve("app/data/ASCII");
        Fx f = new Fx(dir);
        Files.copy(acct, f.dd.get("ACCTFILE"));
        Files.copy(tcat, f.dd.get("TCATBALF"));
        Files.copy(a.resolve("cardxref.txt"), f.dd.get("XREFFILE"));
        Files.copy(a.resolve("discgrp.txt"), f.dd.get("DISCGRP"));
        Files.writeString(f.dd.get("TRANSACT"), "");
        f.run("2022071800");
        Path g = root.resolve("legacy/golden").resolve(scenario);
        assertArrayEquals(Files.readAllBytes(g.resolve("TRANSACT.txt")), Files.readAllBytes(f.dd.get("TRANSACT")), "TRANSACT");
        assertArrayEquals(Files.readAllBytes(g.resolve("ACCTFILE.txt")), Files.readAllBytes(f.dd.get("ACCTFILE")), "ACCTFILE");
        assertArrayEquals(Files.readAllBytes(g.resolve("SYSOUT.txt")), f.sysout.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), "SYSOUT");
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(f.rc), "RC");
    }

    @Test
    void stock() throws Exception {
        Path a = Fx.repo().resolve("app/data/ASCII");
        check("CBACT04C", a.resolve("acctdata.txt"), a.resolve("tcatbal.txt"));
    }

    @Test
    void afterCbtrn02c() throws Exception {
        Path g = Fx.repo().resolve("legacy/golden/CBTRN02C");
        check("CBACT04C-after-CBTRN02C", g.resolve("ACCTFILE.txt"), g.resolve("TCATBALF.txt"));
    }

    @Test
    void lastAccountOfGoldenRunIsUnchanged_D1() throws Exception {
        Path g = Fx.repo().resolve("legacy/golden");
        String in = Files.readAllLines(g.resolve("CBTRN02C/ACCTFILE.txt")).stream()
                .filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        String out = Files.readAllLines(g.resolve("CBACT04C-after-CBTRN02C/ACCTFILE.txt")).stream()
                .filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        assertEquals(in, out);
        boolean tx = Files.readAllLines(g.resolve("CBACT04C-after-CBTRN02C/TRANSACT.txt")).stream()
                .anyMatch(l -> l.startsWith("2022071800000050"));
        assertEquals(true, tx);
    }
}
