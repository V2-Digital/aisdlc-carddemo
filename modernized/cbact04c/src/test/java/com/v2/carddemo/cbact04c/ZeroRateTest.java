package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZeroRateTest {
    @TempDir Path tmp;

    @Test void type03IsSkippedInAfterCbtrn02cScenario() throws Exception {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Path g = GoldenTest.GOLDEN.resolve("CBTRN02C");
        Files.copy(g.resolve("ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(g.resolve("TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        for (String[] f : new String[][] {{"cardxref", "XREFFILE"}, {"discgrp", "DISCGRP"}})
            Files.copy(GoldenTest.SAMPLE.resolve(f[0] + ".txt"), in.resolve(f[1] + ".txt"));
        Support s = Support.run(in, tmp.resolve("out"));
        long type03 = Files.readAllLines(in.resolve("TCATBALF.txt")).stream().filter(l -> l.startsWith("03", 11)).count();
        assertEquals(50, type03);
        assertEquals(50, s.transact.size()); // 100 tcatbal rows, 50 type-03 rows produce nothing
        assertTrue(s.transact.stream().allMatch(t -> t.startsWith("01", 16)));
    }

    @Test void zeroRateAddsNothingAndStillZeroesCycleFields() throws Exception {
        Support s = Support.run(Support.EDGE.resolve("ok"), tmp);
        assertEquals(5, s.transact.size()); // 6 tcatbal rows, acct 4 type 03 skipped
        String a4 = s.accounts.get(3);
        assertEquals("00000000004", a4.substring(0, 11));
        assertEquals("00000000400{", a4.substring(12, 24)); // balance unchanged (D7: cycle zeroed anyway)
        assertEquals("00000000000{00000000000{", a4.substring(78, 102));
    }
}
