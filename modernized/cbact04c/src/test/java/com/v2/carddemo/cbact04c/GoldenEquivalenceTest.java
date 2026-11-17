package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path dir;
    static final Path GOLD = Fx.ROOT.resolve("legacy/golden");
    static final Path SAMPLE = Fx.ROOT.resolve("app/data/ASCII");

    private void check(String scenario, Path tcat, Path acct) throws IOException {
        Path g = GOLD.resolve(scenario);
        Path acctCopy = Files.copy(acct, dir.resolve("ACCTFILE"));
        Map<String, String> env = new HashMap<>();
        env.put("DD_TCATBALF", tcat.toString());
        env.put("DD_XREFFILE", SAMPLE.resolve("cardxref.txt").toString());
        env.put("DD_DISCGRP", SAMPLE.resolve("discgrp.txt").toString());
        env.put("DD_ACCTFILE", acctCopy.toString());
        env.put("DD_TRANSACT", dir.resolve("TRANSACT").toString());
        env.put("CARDDEMO_CURRENT_DATE", Fx.FROZEN);
        Fx.Result r = Fx.exec(dir, env, "2022071800");
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(r.rc()));
        assertArrayEquals(Files.readAllBytes(g.resolve("TRANSACT.txt")), Files.readAllBytes(dir.resolve("TRANSACT")), "TRANSACT");
        assertArrayEquals(Files.readAllBytes(g.resolve("ACCTFILE.txt")), Files.readAllBytes(dir.resolve("ACCTFILE")), "ACCTFILE");
        assertEquals(Files.readString(g.resolve("SYSOUT.txt")), r.sysout(), "SYSOUT");
    }

    @Test void stockData() throws IOException {
        check("CBACT04C", SAMPLE.resolve("tcatbal.txt"), SAMPLE.resolve("acctdata.txt"));
    }

    @Test void afterCbtrn02c() throws IOException {
        Path g = GOLD.resolve("CBTRN02C");
        check("CBACT04C-after-CBTRN02C", g.resolve("TCATBALF.txt"), g.resolve("ACCTFILE.txt"));
    }
}
