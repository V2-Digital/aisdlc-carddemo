package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: each pins a legacy defect that must NOT be fixed in CR-1. */
class DefectTest {
    @Test
    void d1_lastAccountNotRewrittenButItsInterestIsStillPosted(@TempDir Path t) throws Exception {
        Path c = Fx.GOLDEN.resolve("CBTRN02C");
        Path in = Fx.stage(t.resolve("in"), c.resolve("TCATBALF.txt"), c.resolve("ACCTFILE.txt"));
        Fx.run(in, t.resolve("out"), Fx.NOW);
        String inRec = Fx.lines(c.resolve("ACCTFILE.txt")).stream().filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        String outRec = Fx.lines(t.resolve("out/ACCTFILE.txt")).stream().filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        assertEquals(inRec, outRec, "account 50 must be unchanged (D1)");
        assertTrue(outRec.contains("00000019458G"));
        String tx = Fx.lines(t.resolve("out/TRANSACT.txt")).stream().filter(l -> l.startsWith("2022071800000050")).findFirst().orElseThrow();
        assertEquals("0000000187G", tx.substring(132, 143));
        // and exactly the other 49 changed
        List<String> a = Fx.lines(c.resolve("ACCTFILE.txt")), b = Fx.lines(t.resolve("out/ACCTFILE.txt"));
        long changed = java.util.stream.IntStream.range(0, a.size()).filter(i -> !a.get(i).equals(b.get(i))).count();
        assertEquals(49, changed);
    }

    @Test
    void d4_blankGroupAlwaysFallsBackToDefault(@TempDir Path t) throws Exception {
        Path in = Fx.stage(t.resolve("in"), Fx.DATA.resolve("tcatbal.txt"), Fx.DATA.resolve("acctdata.txt"));
        Fx.Result r = Fx.run(in, t.resolve("out"), Fx.NOW);
        assertEquals(50, r.sysout().stream().filter("DISCLOSURE GROUP RECORD MISSING"::equals).count());
    }

    @Test
    void d5_zeroAmountTransactionsWrittenOnStockData(@TempDir Path t) throws Exception {
        Path in = Fx.stage(t.resolve("in"), Fx.DATA.resolve("tcatbal.txt"), Fx.DATA.resolve("acctdata.txt"));
        Fx.run(in, t.resolve("out"), Fx.NOW);
        assertTrue(Fx.lines(t.resolve("out/TRANSACT.txt")).stream().allMatch(l -> l.startsWith("2022071800") && l.substring(132, 143).equals("0000000000{")));
    }

    @Test
    void d6_discgrpOpenErrorSaysDalyRejects(@TempDir Path t) throws Exception {
        Path in = Fx.stage(t.resolve("in"), Fx.DATA.resolve("tcatbal.txt"), Fx.DATA.resolve("acctdata.txt"));
        java.nio.file.Files.delete(in.resolve("DISCGRP.txt"));
        Fx.Result r = Fx.run(in, t.resolve("out"), Fx.NOW);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR OPENING DALY REJECTS FILE"));
    }

    @Test
    void d7_overflowWrapsSilently(@TempDir Path t) throws Exception {
        // bal 999999999.99 * rate 9999.99 / 1200 overflows S9(9)V99 -> high digits dropped, no exception
        Path in = Fx.synth(t.resolve("in"), List.of(Fx.tcat("00000000001", "01", "0001", "9999999999I")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "99999I")), true, true);
        Fx.Result r = Fx.run(in, t.resolve("out"), Fx.NOW);
        assertEquals(0, r.rc());
        // exact = 999999999.99 * 9999.99 / 1200 = 8333324999.xx (10 integer digits); the leading 8 is dropped
        String amt = Fx.lines(t.resolve("out/TRANSACT.txt")).get(0).substring(132, 143);
        assertEquals("3333249999A", amt);
    }
}
