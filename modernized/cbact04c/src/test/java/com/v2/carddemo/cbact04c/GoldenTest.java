package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    private static final Path ASCII = ROOT.resolve("app/data/ASCII");
    private static final Path GOLDEN = ROOT.resolve("legacy/golden");

    private void check(Path tcatbalf, Path acctfile, String scenario, Path tmp) throws Exception {
        ByteArrayOutputStream sysout = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(sysout, false, StandardCharsets.ISO_8859_1);
        int rc = Main.run(new String[] {"--parm", "2022071800", "--tcatbalf", tcatbalf.toString(),
                "--xreffile", ASCII.resolve("cardxref.txt").toString(), "--acctfile", acctfile.toString(),
                "--discgrp", ASCII.resolve("discgrp.txt").toString(),
                "--transact", tmp.resolve("TRANSACT.txt").toString(),
                "--acctfile-out", tmp.resolve("ACCTFILE.txt").toString()}, "2022071800000000", out);
        out.flush();
        assertEquals(0, rc);
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt"}) {
            assertArrayEquals(Files.readAllBytes(GOLDEN.resolve(scenario).resolve(f)),
                    Files.readAllBytes(tmp.resolve(f)), f);
        }
        assertArrayEquals(Files.readAllBytes(GOLDEN.resolve(scenario).resolve("SYSOUT.txt")),
                sysout.toByteArray(), "SYSOUT");
    }

    @Test
    void stockData(@TempDir Path tmp) throws Exception {
        check(ASCII.resolve("tcatbal.txt"), ASCII.resolve("acctdata.txt"), "CBACT04C", tmp);
    }

    @Test
    void afterCbtrn02c(@TempDir Path tmp) throws Exception {
        check(GOLDEN.resolve("CBTRN02C/TCATBALF.txt"), GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"),
                "CBACT04C-after-CBTRN02C", tmp);
    }
}
