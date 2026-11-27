package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GoldenTest {
    private static void compare(String scenario, Path inputDir, int tranRecs, int sysoutLines) throws IOException {
        Path gold = Fx.ROOT.resolve("legacy/golden").resolve(scenario);
        Path out = Path.of("target/out").resolve(scenario);
        Fx.Run r = Fx.runStock(out, inputDir);
        assertEquals(Files.readString(gold.resolve("RC.txt")).trim(), String.valueOf(r.rc()));
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt"}) {
            assertEquals(-1, Files.mismatch(gold.resolve(f), out.resolve(f)), f + " differs from golden");
        }
        assertEquals(Files.readString(gold.resolve("SYSOUT.txt")), r.sysout(), "SYSOUT differs");
        assertEquals(tranRecs, Files.readAllLines(out.resolve("TRANSACT.txt")).size());
        assertEquals(50, Files.readAllLines(out.resolve("ACCTFILE.txt")).size());
        assertEquals(sysoutLines, r.sysout().split("\n").length);
        assertEquals(tranRecs * 351L, Files.size(out.resolve("TRANSACT.txt")));
        assertEquals(50 * 301L, Files.size(out.resolve("ACCTFILE.txt")));
    }

    @Test
    void stock() throws IOException {
        compare("CBACT04C", null, 50, 152);
    }

    @Test
    void afterCbtrn02c() throws IOException {
        compare("CBACT04C-after-CBTRN02C", Fx.ROOT.resolve("legacy/golden/CBTRN02C"), 50, 302);
    }
}
