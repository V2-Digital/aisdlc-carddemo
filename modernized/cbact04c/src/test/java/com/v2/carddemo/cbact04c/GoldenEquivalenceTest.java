package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    private void check(Path tmp, String scenario, Path tcat, Path acct, int sysoutLines) throws Exception {
        Fixtures.Result r = Fixtures.runMain(tmp, tcat, Fixtures.data("cardxref.txt"), acct, Fixtures.data("discgrp.txt"));
        assertEquals(0, r.rc());
        assertEquals("0", Fixtures.lines(Fixtures.golden(scenario, "RC.txt")).get(0).trim());
        assertArrayEquals(Fixtures.bytes(Fixtures.golden(scenario, "TRANSACT.txt")), Fixtures.bytes(tmp.resolve("TRANSACT.txt")), "TRANSACT");
        assertArrayEquals(Fixtures.bytes(Fixtures.golden(scenario, "ACCTFILE.txt")), Fixtures.bytes(tmp.resolve("ACCTFILE.txt")), "ACCTFILE");
        assertEquals(Fixtures.lines(Fixtures.golden(scenario, "SYSOUT.txt")), r.sysout(), "SYSOUT");
        assertEquals(sysoutLines, r.sysout().size());
    }

    @Test
    void stock(@TempDir Path tmp) throws Exception {
        check(tmp, "CBACT04C", Fixtures.data("tcatbal.txt"), Fixtures.data("acctdata.txt"), 152);
    }

    @Test
    void afterCbtrn02c(@TempDir Path tmp) throws Exception {
        check(tmp, "CBACT04C-after-CBTRN02C", Fixtures.golden("CBTRN02C", "TCATBALF.txt"),
                Fixtures.golden("CBTRN02C", "ACCTFILE.txt"), 302);
    }
}
