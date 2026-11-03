package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterizes legacy defect D1: the last account in TCATBALF is never rewritten. */
class LastAccountDefectTest {
    @TempDir Path tmp;

    @Test void D1_lastAccountInterestNeverPosted_acct50() throws Exception {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Path g = GoldenTest.GOLDEN.resolve("CBTRN02C");
        Files.copy(g.resolve("ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(g.resolve("TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        for (String[] f : new String[][] {{"cardxref", "XREFFILE"}, {"discgrp", "DISCGRP"}})
            Files.copy(GoldenTest.SAMPLE.resolve(f[0] + ".txt"), in.resolve(f[1] + ".txt"));
        Support s = Support.run(in, tmp.resolve("out"));

        String before = Files.readAllLines(g.resolve("ACCTFILE.txt")).get(49);
        String after = s.accounts.get(49);
        assertEquals("00000000050", after.substring(0, 11));
        assertEquals(before, after); // unchanged, cycle fields included
        assertEquals("00000019458G", after.substring(12, 24)); // 1945.87
        String tx50 = s.transact.stream().filter(t -> t.contains("Int. for a/c 00000000050")).findFirst().orElseThrow();
        assertEquals("0000000187G", Support.amount(tx50)); // 18.77 is still charged in TRANSACT
    }

    @Test void D1_syntheticLastAccountKeepsBalance() throws Exception {
        Support s = Support.run(Support.EDGE.resolve("ok"), tmp);
        String in5 = Files.readAllLines(Support.EDGE.resolve("ok/ACCTFILE.txt")).get(4);
        assertEquals(in5, s.accounts.get(4));
        assertEquals("0000001250{", Support.amount(s.transact.get(4))); // its interest tx is still written
    }
}
