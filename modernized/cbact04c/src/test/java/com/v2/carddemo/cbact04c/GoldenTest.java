package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private static final Path ROOT = Path.of(System.getProperty("basedir", ".")).toAbsolutePath()
            .resolve("../..").normalize();
    private static final Path SAMPLE = ROOT.resolve("app/data/ASCII");
    private static final Path GOLDEN = ROOT.resolve("legacy/golden");

    @TempDir
    Path tmp;

    private void stock(Path in) throws Exception {
        Files.copy(SAMPLE.resolve("tcatbal.txt"), in.resolve("TCATBALF.txt"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(SAMPLE.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(SAMPLE.resolve("acctdata.txt"), in.resolve("ACCTFILE.txt"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(SAMPLE.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"), StandardCopyOption.REPLACE_EXISTING);
    }

    private void check(Path in, String scenario) throws Exception {
        Path out = Files.createDirectory(tmp.resolve("out"));
        ByteArrayOutputStream sysout = new ByteArrayOutputStream();
        int rc = new Cbact04c(Main.clock("2022071800000000"), new PrintStream(sysout, true, StandardCharsets.ISO_8859_1))
                .run("2022071800", in, out);
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
    void stock() throws Exception {
        Path in = Files.createDirectory(tmp.resolve("in"));
        stock(in);
        check(in, "CBACT04C");
    }

    @Test
    void afterCbtrn02c() throws Exception {
        Path in = Files.createDirectory(tmp.resolve("in"));
        stock(in);
        for (String f : new String[] {"ACCTFILE.txt", "TCATBALF.txt"}) {
            Files.copy(GOLDEN.resolve("CBTRN02C").resolve(f), in.resolve(f), StandardCopyOption.REPLACE_EXISTING);
        }
        check(in, "CBACT04C-after-CBTRN02C");
    }
}
