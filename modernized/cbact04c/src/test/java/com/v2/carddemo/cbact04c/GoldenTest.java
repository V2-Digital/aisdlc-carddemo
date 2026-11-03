package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path GOLDEN = ROOT.resolve("legacy/golden");
    static final Path SAMPLE = ROOT.resolve("app/data/ASCII");

    @TempDir Path tmp;

    private void check(Path in, String scenario) throws Exception {
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        Path out = tmp.resolve("out");
        int rc = new Cbact04c(in, out, "2022071800", Main.clock("2022071800000000"),
                new PrintStream(so, true, "ISO-8859-1"), new PrintStream(new ByteArrayOutputStream())).run();
        Path g = GOLDEN.resolve(scenario);
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(rc));
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt"})
            assertArrayEquals(Files.readAllBytes(g.resolve(f)), Files.readAllBytes(out.resolve(f)), f);
        assertArrayEquals(Files.readAllBytes(g.resolve("SYSOUT.txt")), so.toByteArray(), "SYSOUT");
    }

    private Path stockInputs() throws Exception {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Files.copy(SAMPLE.resolve("tcatbal.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(SAMPLE.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(SAMPLE.resolve("acctdata.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(SAMPLE.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        return in;
    }

    @Test
    void stock() throws Exception {
        check(stockInputs(), "CBACT04C");
    }

    @Test
    void afterCbtrn02c() throws Exception {
        Path in = stockInputs();
        Files.copy(GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"), in.resolve("ACCTFILE.txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(GOLDEN.resolve("CBTRN02C/TCATBALF.txt"), in.resolve("TCATBALF.txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        check(in, "CBACT04C-after-CBTRN02C");
    }
}
