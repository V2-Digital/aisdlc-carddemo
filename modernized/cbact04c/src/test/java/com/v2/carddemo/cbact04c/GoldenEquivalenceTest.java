package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    private static final Path ROOT = Path.of(System.getProperty("repo.root", "../.."));
    private static final Path ASCII = ROOT.resolve("app/data/ASCII");
    private static final Path GOLD = ROOT.resolve("legacy/golden");

    private void check(Path tcat, Path acct, String scenario, Path out, long trans, long accts) throws IOException {
        int rc = Main.run(new String[] {"2022071800",
            "--TCATBALF=" + tcat, "--XREFFILE=" + ASCII.resolve("cardxref.txt"), "--ACCTFILE=" + acct,
            "--DISCGRP=" + ASCII.resolve("discgrp.txt"), "--TRANSACT=" + out.resolve("TRANSACT.txt"),
            "--ACCTOUT=" + out.resolve("ACCTFILE.txt"), "--SYSOUT=" + out.resolve("SYSOUT.txt")},
            "2022071800000000");
        assertEquals(Integer.parseInt(Files.readString(GOLD.resolve(scenario).resolve("RC.txt")).trim()), rc);
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt", "SYSOUT.txt"}) {
            assertArrayEquals(Files.readAllBytes(GOLD.resolve(scenario).resolve(f)),
                Files.readAllBytes(out.resolve(f)), f + " differs from golden " + scenario);
        }
        assertEquals(trans * 351, Files.size(out.resolve("TRANSACT.txt")) );
        assertEquals(accts * 301, Files.size(out.resolve("ACCTFILE.txt")));
    }

    @Test
    void stock(@TempDir Path out) throws IOException {
        check(ASCII.resolve("tcatbal.txt"), ASCII.resolve("acctdata.txt"), "CBACT04C", out, 50, 50);
    }

    @Test
    void afterPosting(@TempDir Path out) throws IOException {
        Path g = GOLD.resolve("CBTRN02C");
        check(g.resolve("TCATBALF.txt"), g.resolve("ACCTFILE.txt"), "CBACT04C-after-CBTRN02C", out, 50, 50);
    }
}
