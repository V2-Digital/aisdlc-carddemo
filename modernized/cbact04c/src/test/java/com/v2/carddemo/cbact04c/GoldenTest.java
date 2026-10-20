package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private static final Path ROOT = Path.of("../..");
    private static final Path GOLDEN = ROOT.resolve("legacy/golden");
    private static final Path SAMPLE = ROOT.resolve("app/data/ASCII");

    @TempDir
    Path tmp;

    private void check(Path in, String scenario) throws IOException {
        Path out = tmp.resolve("out");
        ByteArrayOutputStream sysout = new ByteArrayOutputStream();
        int rc = new Cbact04c(in, out, "2022071800", Main.clock("2022071800000000"),
                new PrintStream(sysout, true, StandardCharsets.ISO_8859_1), System.err).run();
        Path g = GOLDEN.resolve(scenario);
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(rc));
        assertEquals(Files.readString(g.resolve("SYSOUT.txt"), StandardCharsets.ISO_8859_1),
                sysout.toString(StandardCharsets.ISO_8859_1));
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt"}) {
            assertEquals(Files.readString(g.resolve(f), StandardCharsets.ISO_8859_1),
                    Files.readString(out.resolve(f), StandardCharsets.ISO_8859_1), f);
        }
    }

    @Test
    void stock() throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Files.copy(SAMPLE.resolve("tcatbal.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(SAMPLE.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(SAMPLE.resolve("acctdata.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(SAMPLE.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        check(in, "CBACT04C");
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Files.copy(GOLDEN.resolve("CBTRN02C/TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(SAMPLE.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(SAMPLE.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        check(in, "CBACT04C-after-CBTRN02C");
    }
}
