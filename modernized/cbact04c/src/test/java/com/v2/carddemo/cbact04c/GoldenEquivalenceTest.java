package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path tmp;

    private void prepare(Path in, Path acctSrc, Path tcatSrc) throws Exception {
        Files.copy(T.ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(T.ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        Files.copy(acctSrc, in.resolve("ACCTFILE.txt"));
        Files.copy(tcatSrc, in.resolve("TCATBALF.txt"));
    }

    private void check(String scenario, int tranRecords, int sysoutLines) throws Exception {
        Path in = tmp.resolve("in"), out = tmp.resolve("out");
        Files.createDirectories(in);
        T.Result r = T.run("2022071800", in, out);
        Path g = T.GOLDEN.resolve(scenario);
        assertEquals(0, r.rc());
        assertArrayEquals(T.bytes(g.resolve("TRANSACT.txt")), T.bytes(out.resolve("TRANSACT.txt")), "TRANSACT");
        assertArrayEquals(T.bytes(g.resolve("ACCTFILE.txt")), T.bytes(out.resolve("ACCTFILE.txt")), "ACCTFILE");
        assertArrayEquals(T.bytes(g.resolve("SYSOUT.txt")), r.out().getBytes("ISO-8859-1"), "SYSOUT");
        assertEquals(tranRecords, Files.readAllLines(out.resolve("TRANSACT.txt")).size());
        assertEquals(50, Files.readAllLines(out.resolve("ACCTFILE.txt")).size());
        assertEquals(sysoutLines, Files.readAllLines(g.resolve("SYSOUT.txt")).size());
    }

    @Test
    void stock() throws Exception {
        Files.createDirectories(tmp.resolve("in"));
        prepare(tmp.resolve("in"), T.ASCII.resolve("acctdata.txt"), T.ASCII.resolve("tcatbal.txt")); // CRLF input
        check("CBACT04C", 50, 152);
    }

    @Test
    void afterCbtrn02c() throws Exception {
        Files.createDirectories(tmp.resolve("in"));
        Path g = T.GOLDEN.resolve("CBTRN02C");
        prepare(tmp.resolve("in"), g.resolve("ACCTFILE.txt"), g.resolve("TCATBALF.txt"));
        check("CBACT04C-after-CBTRN02C", 50, 302);
    }
}
