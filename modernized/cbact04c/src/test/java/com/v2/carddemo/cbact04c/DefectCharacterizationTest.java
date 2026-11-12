package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy defects on purpose. Do not "fix" these in CR-1. */
class DefectCharacterizationTest {
    @TempDir Path tmp;

    private Fixture base(String bal11) {
        Fixture f = new Fixture(tmp);
        f.acct = Fixture.account("00000000001", "00000001000{", "") + "\n"
                + Fixture.account("00000000002", "00000001000{", "") + "\n";
        f.tcat = Fixture.tcat("00000000001", "01", "0001", bal11) + "\n"
                + Fixture.tcat("00000000002", "01", "0001", bal11) + "\n";
        f.xref = Fixture.xref("1111222233334444", "00000000001") + "\n"
                + Fixture.xref("5555666677778888", "00000000002") + "\n";
        f.disc = Fixture.disc("DEFAULT", "01", "0001", "000150").substring(0, 22) + "\n";
        return f;
    }

    @Test
    void f1_lastAccountNotWrittenBack() throws Exception {
        Fixture f = base("0000151277{");
        assertEquals(0, f.run());
        List<String> out = f.lines("ACCTFILE_OUT.txt", 300);
        // account 1: interest 18.90 added to 100.00 (-> 118.90) and cycle fields zeroed
        assertEquals("00000001189{", out.get(0).substring(12, 24));
        assertEquals("00000000000{", out.get(0).substring(78, 90));
        assertEquals("00000000000{", out.get(0).substring(90, 102));
        // account 2 (last in TCATBALF) earned interest too, yet is byte-identical to its input
        assertEquals(Fixture.account("00000000002", "00000001000{", ""), out.get(1));
        assertEquals(2, f.lines("TRANSACT.txt", 350).size());
    }

    @Test
    void f2_interestTruncatedNotRounded() throws Exception {
        // 1512.77 * 1.50 / 1200 = 18.909625 -> 18.90 (half-up would give 18.91)
        Fixture f = base("0000151277{");
        f.run();
        assertEquals("0000000189{", f.lines("TRANSACT.txt", 350).get(0).substring(132, 143));
    }

    @Test
    void f3_zeroAmountTransactionWritten() throws Exception {
        Fixture f = base("0000000000{");
        f.run();
        List<String> t = f.lines("TRANSACT.txt", 350);
        assertEquals(2, t.size());
        assertEquals("0000000000{", t.get(0).substring(132, 143));
    }

    @Test
    void negativeInterestTruncatedToZeroIsWrittenPositive() throws Exception {
        // -0.01 * 1.50 / 1200 truncates to -0.00; real COBOL writes '{'
        // (legacy/tests/cbact04c_negzero.sh)
        Fixture f = base("0000000000J");
        assertEquals(0, f.run());
        assertEquals("0000000000{", f.lines("TRANSACT.txt", 350).get(0).substring(132, 143));
    }

    @Test
    void defaultGroupFallbackAndAbendWhenMissing() throws Exception {
        Fixture f = base("0000151277{");
        assertEquals(0, f.run());
        assertTrue(f.sysout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));

        Fixture g = base("0000151277{");
        g.disc = Fixture.disc("OTHER", "01", "0001", "000150").substring(0, 22) + "\n";
        assertEquals(16, g.run());
        assertTrue(g.sysout().contains("ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
    }
}
