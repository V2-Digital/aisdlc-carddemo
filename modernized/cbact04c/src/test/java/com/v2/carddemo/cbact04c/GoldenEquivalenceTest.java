package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path tmp;
    final Path root = Path.of(Fx.repoRoot());

    private void check(String scenario, Path acctSrc, Path tcatSrc) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path ascii = root.resolve("app/data/ASCII");
        Files.copy(tcatSrc, in.resolve("TCATBALF.txt"));
        Files.copy(acctSrc, in.resolve("ACCTFILE.txt"));
        Files.copy(ascii.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ascii.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        Path out = tmp.resolve("out");
        Fx.Result r = Fx.run(in, out);
        Path g = root.resolve("legacy/golden/" + scenario);
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(r.rc));
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt", "SYSOUT.txt"}) {
            assertArrayEquals(Files.readAllBytes(g.resolve(f)), Files.readAllBytes(out.resolve(f)), f);
        }
        assertEquals(50, r.tran.size());
        assertEquals(50, r.acct.size());
    }

    @Test
    void stock() throws IOException {
        Path a = root.resolve("app/data/ASCII");
        check("CBACT04C", a.resolve("acctdata.txt"), a.resolve("tcatbal.txt"));
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path g = root.resolve("legacy/golden/CBTRN02C");
        check("CBACT04C-after-CBTRN02C", g.resolve("ACCTFILE.txt"), g.resolve("TCATBALF.txt"));
    }
}
