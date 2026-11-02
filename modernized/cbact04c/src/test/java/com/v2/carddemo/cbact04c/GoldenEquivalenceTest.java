package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    static final Path ASCII = Fx.ROOT.resolve("app/data/ASCII");
    static final Path GOLD = Fx.ROOT.resolve("legacy/golden");

    @TempDir Path tmp;

    private void stock(Path in) throws IOException {
        Files.copy(ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
    }

    private void compare(Fx.Run r, String scenario, int expectTran) throws IOException {
        Path g = GOLD.resolve(scenario);
        for (String f : new String[] {"TRANSACT", "ACCTFILE"}) {
            assertArrayEquals(Files.readAllBytes(g.resolve(f + ".txt")),
                    Files.readAllBytes(r.out().resolve(f + ".txt")), f);
        }
        assertEquals(expectTran, r.lines("TRANSACT").size());
        assertEquals(Files.readString(g.resolve("SYSOUT.txt")), r.sysout(), "SYSOUT");
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(r.rc()), "RC");
    }

    @Test
    void stock() throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        stock(in);
        Files.copy(ASCII.resolve("tcatbal.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(ASCII.resolve("acctdata.txt"), in.resolve("ACCTFILE.txt"));
        compare(Fx.run(in, tmp.resolve("out")), "CBACT04C", 50);
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        stock(in);
        Path prev = GOLD.resolve("CBTRN02C");
        Files.copy(prev.resolve("TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(prev.resolve("ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        compare(Fx.run(in, tmp.resolve("out")), "CBACT04C-after-CBTRN02C", 50);
    }
}
