package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private void assertEqualsGolden(Result r, String scenario, int tx, int accts, int sysoutLines) throws IOException {
        Path g = GOLDEN.resolve(scenario);
        assertEquals(Integer.parseInt(text(g.resolve("RC.txt")).trim()), r.rc());
        assertEquals(0, r.rc());
        assertArrayEquals(bytes(g.resolve("TRANSACT.txt")), bytes(r.out().resolve("TRANSACT.txt")), "TRANSACT");
        assertArrayEquals(bytes(g.resolve("ACCTFILE.txt")), bytes(r.out().resolve("ACCTFILE.txt")), "ACCTFILE");
        assertEquals(text(g.resolve("SYSOUT.txt")), r.stdout(), "SYSOUT");
        assertEquals(tx, text(g.resolve("TRANSACT.txt")).lines().count());
        assertEquals(accts, text(g.resolve("ACCTFILE.txt")).lines().count());
        assertEquals(sysoutLines, text(g.resolve("SYSOUT.txt")).lines().count());
    }

    @Test
    void stock(@TempDir Path out) throws IOException {
        Result r = run(ASCII.resolve("tcatbal.txt"), ASCII.resolve("cardxref.txt"), ASCII.resolve("discgrp.txt"),
                ASCII.resolve("acctdata.txt"), out);
        assertEqualsGolden(r, "CBACT04C", 50, 50, 152);
    }

    @Test
    void afterCbtrn02c(@TempDir Path out) throws IOException {
        Path prev = GOLDEN.resolve("CBTRN02C");
        Result r = run(prev.resolve("TCATBALF.txt"), ASCII.resolve("cardxref.txt"), ASCII.resolve("discgrp.txt"),
                prev.resolve("ACCTFILE.txt"), out);
        assertEqualsGolden(r, "CBACT04C-after-CBTRN02C", 50, 50, 302);
    }
}
