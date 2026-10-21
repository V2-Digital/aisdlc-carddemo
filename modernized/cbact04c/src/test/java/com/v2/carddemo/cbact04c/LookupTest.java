package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LookupTest {
    @TempDir Path tmp;

    private Path in() throws IOException {
        return Fx.stockInputs(Files.createDirectory(tmp.resolve("in")));
    }

    private void tcat(Path in, String... accts) throws IOException {
        List<String> l = new ArrayList<>();
        for (String a : accts) {
            l.add(Fx.tcat(a, "01", "0001", "00000100000"));
        }
        Fx.put(in, "TCATBALF", l);
    }

    @Test
    void groupHit() throws IOException {
        Path in = in();
        // group-id (cols 113-122) ZEROAPR has 01/0001 rate 0 while DEFAULT has 15.00: a hit means no txn
        List<String> accts = Fx.lines(in.resolve("ACCTFILE.txt"));
        accts.set(0, Fx.lineWith(accts.get(0), 112, "ZEROAPR"));
        Fx.put(in, "ACCTFILE", accts);
        tcat(in, "00000000001");
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(0, r.rc());
        assertFalse(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING"));
        assertEquals(0, Fx.lines(r.out().resolve("TRANSACT.txt")).size());
    }

    @Test
    void defaultFallback() throws IOException {
        Path in = in();
        tcat(in, "00000000001", "00000000002");
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertTrue(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        // 1000.00*15/1200 = 12.50
        assertEquals("0000000125{", Fx.lines(r.out().resolve("TRANSACT.txt")).get(0).substring(132, 143));
    }

    private void expectAbend(Path in, String... msgs) throws IOException {
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(16, r.rc());
        for (String m : msgs) {
            assertTrue(r.sysout().contains(m), m);
        }
        assertTrue(r.sysout().contains("ABENDING PROGRAM"));
        assertTrue(r.syserr().contains("CEE3ABD: ABEND U0999"));
        assertFalse(Files.exists(r.out().resolve("TRANSACT.txt")), "nothing exported on abend");
    }

    @Test
    void defaultMissing_16() throws IOException {
        Path in = in();
        tcat(in, "00000000001");
        List<String> d = new ArrayList<>(Fx.lines(in.resolve("DISCGRP.txt")));
        d.removeIf(l -> l.startsWith("DEFAULT   01" + "0001"));
        Fx.put(in, "DISCGRP", d);
        expectAbend(in, "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023");
    }

    @Test
    void accountMissing_16() throws IOException {
        Path in = in();
        tcat(in, "00000099999");
        expectAbend(in, "ACCOUNT NOT FOUND: 00000099999", "ERROR READING ACCOUNT FILE");
    }

    @Test
    void xrefMissing_16() throws IOException {
        Path in = in();
        tcat(in, "00000000001");
        List<String> x = new ArrayList<>(Fx.lines(in.resolve("XREFFILE.txt")));
        x.removeIf(l -> l.startsWith("00000000001", 25));
        Fx.put(in, "XREFFILE", x);
        expectAbend(in, "ACCOUNT NOT FOUND: 00000000001", "ERROR READING XREF FILE");
    }

    @Test
    void emptyTcatbal() throws IOException {
        Path in = in();
        Fx.put(in, "TCATBALF", List.of());
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(0, r.rc());
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n", r.sysout());
        assertEquals(0, Files.size(r.out().resolve("TRANSACT.txt")));
    }
}
