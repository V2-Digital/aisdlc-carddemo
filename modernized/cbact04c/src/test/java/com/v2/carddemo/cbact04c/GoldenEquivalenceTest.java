package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path tmp;

    private void check(String scenario, Path acct, Path tcat, int records) throws IOException {
        Path golden = Support.ROOT.resolve("legacy/golden").resolve(scenario);
        Support.Result r = Support.run(tcat, Support.STOCK.resolve("cardxref.txt"), acct,
                Support.STOCK.resolve("discgrp.txt"), tmp.resolve("out"));
        assertArrayEquals(Files.readAllBytes(golden.resolve("TRANSACT.txt")),
                Files.readAllBytes(tmp.resolve("out/TRANSACT.txt")), "TRANSACT");
        assertArrayEquals(Files.readAllBytes(golden.resolve("ACCTFILE.txt")),
                Files.readAllBytes(tmp.resolve("out/ACCTFILE.txt")), "ACCTFILE");
        assertEquals(Files.readString(golden.resolve("SYSOUT.txt")), r.sysout(), "SYSOUT");
        assertEquals(Files.readString(golden.resolve("RC.txt")).trim(), String.valueOf(r.rc()));
        assertEquals(50, r.acct().size());
        assertEquals(records, r.transact().size());
    }

    @Test
    void stock() throws IOException {
        check("CBACT04C", Support.STOCK.resolve("acctdata.txt"), Support.STOCK.resolve("tcatbal.txt"), 50);
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path g = Support.ROOT.resolve("legacy/golden/CBTRN02C");
        check("CBACT04C-after-CBTRN02C", g.resolve("ACCTFILE.txt"), g.resolve("TCATBALF.txt"), 50);
    }
}
