package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    private static final Path DATA = Fx.ROOT.resolve("app/data/ASCII");
    private static final Path GOLD = Fx.ROOT.resolve("legacy/golden");

    private static void compare(Map<String, Path> in, Path golden, Path out, int tx, int accts, int sysout)
            throws IOException {
        int rc = Cbact04c.run(in, out, Fx.PARM, () -> Fx.CLOCK);
        assertEquals(0, rc);
        for (String f : new String[] {"TRANSACT", "ACCTFILE", "SYSOUT", "RC"}) {
            assertArrayEquals(Files.readAllBytes(golden.resolve(f + ".txt")),
                    Files.readAllBytes(out.resolve(f + ".txt")), f + ".txt differs from golden");
        }
        assertEquals(tx, Files.readAllLines(out.resolve("TRANSACT.txt")).size());
        assertEquals(accts, Files.readAllLines(out.resolve("ACCTFILE.txt")).size());
        assertEquals(sysout, Files.readAllLines(out.resolve("SYSOUT.txt")).size());
    }

    @Test
    void stock(@TempDir Path tmp) throws IOException {
        compare(Map.of("TCATBALF", DATA.resolve("tcatbal.txt"), "XREFFILE", DATA.resolve("cardxref.txt"),
                "ACCTFILE", DATA.resolve("acctdata.txt"), "DISCGRP", DATA.resolve("discgrp.txt")),
                GOLD.resolve("CBACT04C"), tmp, 50, 50, 152);
    }

    @Test
    void afterPosting(@TempDir Path tmp) throws IOException {
        Path posted = GOLD.resolve("CBTRN02C");
        compare(Map.of("TCATBALF", posted.resolve("TCATBALF.txt"), "XREFFILE", DATA.resolve("cardxref.txt"),
                "ACCTFILE", posted.resolve("ACCTFILE.txt"), "DISCGRP", DATA.resolve("discgrp.txt")),
                GOLD.resolve("CBACT04C-after-CBTRN02C"), tmp, 50, 50, 302);
    }
}
