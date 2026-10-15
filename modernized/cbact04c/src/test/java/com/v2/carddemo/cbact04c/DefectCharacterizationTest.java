package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy defects that must NOT be fixed in CR-1. If one of these fails, behaviour drifted from COBOL. */
class DefectCharacterizationTest {
    @Test
    void D1_lastAccountIsNeverRewritten(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = Fixtures.runMain(tmp, Fixtures.golden("CBTRN02C", "TCATBALF.txt"), Fixtures.data("cardxref.txt"),
                Fixtures.golden("CBTRN02C", "ACCTFILE.txt"), Fixtures.data("discgrp.txt"));
        assertEquals(0, r.rc());
        List<String> in = Fixtures.lines(Fixtures.golden("CBTRN02C", "ACCTFILE.txt"));
        List<String> out = Fixtures.lines(tmp.resolve("ACCTFILE.txt"));
        String inAcct = in.stream().filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        String outAcct = out.stream().filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        assertEquals(inAcct, outAcct);
        assertEquals("00000019458G", outAcct.substring(12, 24));
        String tran = Fixtures.lines(tmp.resolve("TRANSACT.txt")).stream()
                .filter(l -> l.startsWith("2022071800000050")).findFirst().orElseThrow();
        assertEquals("0000000187G", tran.substring(132, 143)); // 18.77 charged but never posted
        // every other account did change
        long changed = 0;
        for (int i = 0; i < in.size(); i++) if (!in.get(i).equals(out.get(i))) changed++;
        assertEquals(49, changed);
    }

    @Test
    void D4_noFeeRecordsOnlyInterestTransactions(@TempDir Path tmp) throws Exception {
        Fixtures.runMain(tmp, Fixtures.golden("CBTRN02C", "TCATBALF.txt"), Fixtures.data("cardxref.txt"),
                Fixtures.golden("CBTRN02C", "ACCTFILE.txt"), Fixtures.data("discgrp.txt"));
        for (String l : Fixtures.lines(tmp.resolve("TRANSACT.txt"))) assertEquals("010005", l.substring(16, 22));
    }

    @Test
    void D3_blankGroupAlwaysFallsBackToDefault(@TempDir Path tmp) throws Exception {
        Fixtures.Result r = Fixtures.runMain(tmp, Fixtures.golden("CBTRN02C", "TCATBALF.txt"), Fixtures.data("cardxref.txt"),
                Fixtures.golden("CBTRN02C", "ACCTFILE.txt"), Fixtures.data("discgrp.txt"));
        assertEquals(100, r.sysout().stream().filter("DISCLOSURE GROUP RECORD MISSING"::equals).count());
    }
}
