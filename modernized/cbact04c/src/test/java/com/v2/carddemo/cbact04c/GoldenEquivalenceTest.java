package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path GOLDEN = ROOT.resolve("legacy/golden");
    static final Path ASCII = ROOT.resolve("app/data/ASCII");

    private void check(Path in, String scenario, Path tmp) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int rc = new InterestCalc("2022071800", () -> "2022071800000000", new PrintStream(buf, true, "ISO-8859-1"))
                .run(in, tmp);
        assertEquals(0, rc);
        for (String f : new String[] {"TRANSACT", "ACCTFILE"}) {
            assertArrayEquals(Files.readAllBytes(GOLDEN.resolve(scenario).resolve(f + ".txt")),
                    Files.readAllBytes(tmp.resolve(f + ".txt")), f);
        }
        assertArrayEquals(Files.readAllBytes(GOLDEN.resolve(scenario).resolve("SYSOUT.txt")), buf.toByteArray(), "SYSOUT");
    }

    private Path inputs(Path dir, Path acct, Path tcat) throws Exception {
        Files.copy(acct, dir.resolve("ACCTFILE.txt"));
        Files.copy(tcat, dir.resolve("TCATBALF.txt"));
        Files.copy(ASCII.resolve("cardxref.txt"), dir.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), dir.resolve("DISCGRP.txt"));
        return dir;
    }

    @Test
    void stock(@TempDir Path in, @TempDir Path out) throws Exception {
        check(inputs(in, ASCII.resolve("acctdata.txt"), ASCII.resolve("tcatbal.txt")), "CBACT04C", out);
    }

    @Test
    void afterCbtrn02c(@TempDir Path in, @TempDir Path out) throws Exception {
        Path g = GOLDEN.resolve("CBTRN02C");
        check(inputs(in, g.resolve("ACCTFILE.txt"), g.resolve("TCATBALF.txt")), "CBACT04C-after-CBTRN02C", out);
    }
}
