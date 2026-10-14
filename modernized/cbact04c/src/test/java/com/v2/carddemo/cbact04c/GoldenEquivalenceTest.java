package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    private static final Path ASCII = Path.of("../../app/data/ASCII");
    private static final Path GOLDEN = Path.of("../../legacy/golden");

    @TempDir Path tmp;

    private void check(Path acct, Path tcat, String scenario) throws IOException {
        Files.copy(acct, tmp.resolve("ACCTFILE.txt"));
        Path tc = tmp.resolve("TCATBALF.txt");
        Files.copy(tcat, tc);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int rc = Main.run(new String[] {"--parm", "2022071800", "--clock", "2022071800000000",
                "--TCATBALF", tc.toString(), "--XREFFILE", ASCII.resolve("cardxref.txt").toString(),
                "--DISCGRP", ASCII.resolve("discgrp.txt").toString(),
                "--ACCTFILE", tmp.resolve("ACCTFILE.txt").toString(),
                "--TRANSACT", tmp.resolve("TRANSACT.txt").toString()}, null,
                new PrintStream(buf, true, StandardCharsets.ISO_8859_1));
        Path g = GOLDEN.resolve(scenario);
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), Integer.toString(rc));
        assertArrayEquals(Files.readAllBytes(g.resolve("TRANSACT.txt")), Files.readAllBytes(tmp.resolve("TRANSACT.txt")));
        assertArrayEquals(Files.readAllBytes(g.resolve("ACCTFILE.txt")), Files.readAllBytes(tmp.resolve("ACCTFILE.txt")));
        assertArrayEquals(Files.readAllBytes(g.resolve("SYSOUT.txt")), buf.toByteArray());
    }

    @Test
    void stock() throws IOException {
        // harness loads TCATBALF with CRLF stripped; normalise here too
        Path tc = tmp.resolve("tc-in.txt");
        Files.writeString(tc, Files.readString(ASCII.resolve("tcatbal.txt")).replace("\r", ""));
        check(ASCII.resolve("acctdata.txt"), tc, "CBACT04C");
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path d = GOLDEN.resolve("CBTRN02C");
        check(d.resolve("ACCTFILE.txt"), d.resolve("TCATBALF.txt"), "CBACT04C-after-CBTRN02C");
    }
}
