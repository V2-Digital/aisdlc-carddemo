package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    static final Path G = Path.of("legacy/golden");
    static final String CLOCK = "2022071800000000";

    private void check(String scenario, Path acct, Path tcat, Path tmp) throws Exception {
        Path in = Files.createDirectory(tmp.resolve("in"));
        Path outDir = Files.createDirectory(tmp.resolve("out"));
        Path a = Path.of("app/data/ASCII");
        Files.copy(tcat, in.resolve("TCATBALF.txt"));
        Files.copy(acct, in.resolve("ACCTFILE.txt"));
        Files.copy(a.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(a.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int rc = Cbact04c.run("2022071800", new DdFiles(Map.of(), in, outDir),
                () -> CLOCK, new PrintStream(bos, true), System.err);
        assertEquals(0, rc);
        for (String f : new String[] {"TRANSACT", "ACCTFILE"}) {
            assertArrayEquals(Files.readAllBytes(G.resolve(scenario).resolve(f + ".txt")),
                    Files.readAllBytes(outDir.resolve(f + ".txt")), f);
        }
        assertArrayEquals(Files.readAllBytes(G.resolve(scenario).resolve("SYSOUT.txt")), bos.toByteArray());
    }

    @Test
    void stock(@TempDir Path tmp) throws Exception {
        Path a = Path.of("app/data/ASCII");
        check("CBACT04C", a.resolve("acctdata.txt"), a.resolve("tcatbal.txt"), tmp);
    }

    @Test
    void afterCbtrn02c(@TempDir Path tmp) throws Exception {
        Path b = G.resolve("CBTRN02C");
        check("CBACT04C-after-CBTRN02C", b.resolve("ACCTFILE.txt"), b.resolve("TCATBALF.txt"), tmp);
    }
}
