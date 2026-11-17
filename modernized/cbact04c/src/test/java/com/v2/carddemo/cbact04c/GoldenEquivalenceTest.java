package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path tmp;
    static final Path GOLDEN = Path.of(System.getProperty("golden.dir", "../../legacy/golden"));
    static final Path ASCII = GOLDEN.resolve("../../app/data/ASCII");

    private void scenario(String name, Path acct, Path tcat) throws Exception {
        Path xref = tmp.resolve("XREFFILE.txt"), disc = tmp.resolve("DISCGRP.txt");
        Files.copy(ASCII.resolve("cardxref.txt"), xref);
        Files.copy(ASCII.resolve("discgrp.txt"), disc);
        Path a = tmp.resolve("ACCTFILE.txt"), t = tmp.resolve("TCATBALF.txt"), tr = tmp.resolve("TRANSACT.txt");
        Files.copy(acct, a);
        Files.copy(tcat, t);
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        int rc = Cbact04c.run(new Config("2022071800", t, xref, disc, a, tr,
                Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC),
                new PrintStream(so, true, "ISO-8859-1"), new PrintStream(new ByteArrayOutputStream())));
        Path g = GOLDEN.resolve(name);
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(rc));
        assertArrayEquals(Files.readAllBytes(g.resolve("TRANSACT.txt")), Files.readAllBytes(tr), "TRANSACT");
        assertArrayEquals(Files.readAllBytes(g.resolve("ACCTFILE.txt")), Files.readAllBytes(a), "ACCTFILE");
        assertArrayEquals(Files.readAllBytes(g.resolve("SYSOUT.txt")), so.toByteArray(), "SYSOUT");
    }

    @Test
    void stock() throws Exception {
        scenario("CBACT04C", ASCII.resolve("acctdata.txt"), ASCII.resolve("tcatbal.txt"));
    }

    @Test
    void afterCbtrn02c() throws Exception {
        scenario("CBACT04C-after-CBTRN02C", GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"),
                GOLDEN.resolve("CBTRN02C/TCATBALF.txt"));
    }
}
