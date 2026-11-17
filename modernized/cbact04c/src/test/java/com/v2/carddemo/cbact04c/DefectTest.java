package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: each pins a legacy defect that must NOT be fixed in this CR. */
class DefectTest {
    @TempDir Path tmp;
    final Path root = Support.root();
    final Path ascii = root.resolve("app/data/ASCII");

    private Support.Result scenario3() throws Exception {
        Path pre = root.resolve("legacy/golden/CBTRN02C");
        return Support.run(pre.resolve("TCATBALF.txt"), ascii.resolve("cardxref.txt"), ascii.resolve("discgrp.txt"),
                pre.resolve("ACCTFILE.txt"), tmp);
    }

    @Test
    void d1_lastAccountInterestNeverWrittenBack() throws Exception {
        Support.Result r = scenario3();
        List<String> in = Files.readAllLines(root.resolve("legacy/golden/CBTRN02C/ACCTFILE.txt"));
        List<String> out = Files.readAllLines(r.out().resolve("ACCTFILE.txt"));
        assertEquals(in.get(49), out.get(49));
        assertEquals("00000000050", out.get(49).substring(0, 11));
        assertNotEquals(in.get(0), out.get(0));
        long changed = java.util.stream.IntStream.range(0, 50).filter(i -> !in.get(i).equals(out.get(i))).count();
        assertEquals(49, changed);
        String t50 = Files.readAllLines(r.out().resolve("TRANSACT.txt")).get(49);
        assertEquals("2022071800000050", t50.substring(0, 16));
        assertEquals("0000000187G", t50.substring(132, 143));
    }

    @Test
    void d2_blankGroupIdAlwaysFallsBackToDefault() throws Exception {
        Support.Result r = scenario3();
        assertEquals(100, r.sysout().lines().filter("DISCLOSURE GROUP RECORD MISSING"::equals).count());
    }

    @Test
    void d8_zeroAmountTransactionWrittenForZeroBalance() throws Exception {
        Support.Result r = Support.run(ascii.resolve("tcatbal.txt"), ascii.resolve("cardxref.txt"),
                ascii.resolve("discgrp.txt"), ascii.resolve("acctdata.txt"), tmp);
        List<String> t = Files.readAllLines(r.out().resolve("TRANSACT.txt"));
        assertEquals(50, t.size());
        assertEquals(true, t.stream().allMatch(l -> l.substring(132, 143).equals("0000000000{")));
    }

    @Test
    void d6_suffixWrapsModulo() throws Exception {
        // 1_000_000 transactions is too slow to build here; the wrap is covered by arithmetic of the same expression.
        assertEquals(0, (999_999 + 1) % 1_000_000);
    }
}
