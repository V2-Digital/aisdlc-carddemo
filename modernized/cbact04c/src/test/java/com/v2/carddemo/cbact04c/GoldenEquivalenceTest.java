package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Byte-for-byte comparison with the stored COBOL golden outputs. */
class GoldenEquivalenceTest {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path GOLD = ROOT.resolve("legacy/golden");
    static final Path ASCII = ROOT.resolve("app/data/ASCII");
    @TempDir Path tmp;

    private void check(Path in, String scenario) throws IOException {
        Path out = tmp.resolve("out");
        Cbact04c c = new Cbact04c("2022071800", "2022071800000000");
        c.run(in);
        c.writeOutputs(out);
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt", "SYSOUT.txt", "RC.txt"}) {
            byte[] exp = Files.readAllBytes(GOLD.resolve(scenario).resolve(f));
            byte[] act = Files.readAllBytes(out.resolve(f));
            if (!java.util.Arrays.equals(exp, act)) {
                String[] e = new String(exp, java.nio.charset.StandardCharsets.ISO_8859_1).split("\n", -1);
                String[] a = new String(act, java.nio.charset.StandardCharsets.ISO_8859_1).split("\n", -1);
                for (int i = 0; i < Math.max(e.length, a.length); i++) {
                    String x = i < e.length ? e[i] : "<none>", y = i < a.length ? a[i] : "<none>";
                    if (!x.equals(y)) fail(scenario + "/" + f + " line " + (i + 1) + "\nexpected: " + x + "\nactual:   " + y);
                }
            }
            assertArrayEquals(exp, act, scenario + "/" + f);
        }
    }

    @Test
    void stockInputs() throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Files.copy(ASCII.resolve("tcatbal.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        Files.copy(ASCII.resolve("acctdata.txt"), in.resolve("ACCTFILE.txt"));
        check(in, "CBACT04C");
        assertEquals(50, Files.readAllLines(GOLD.resolve("CBACT04C/TRANSACT.txt")).size());
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Files.copy(GOLD.resolve("CBTRN02C/TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(GOLD.resolve("CBTRN02C/ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        check(in, "CBACT04C-after-CBTRN02C");
    }
}
