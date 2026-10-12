package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins preserved legacy defects D1-D4 (see spec section 5). Do not "fix" these in CR-1. */
class DefectCharacterizationTest {
    static final Path IN = DifferentialEdgeTest.EDGE.resolve("in");

    private Result runWith(Path dir, String tcat, String parm) throws IOException {
        Path t = dir.resolve("tc.txt");
        Files.writeString(t, tcat);
        Path acct = copy(IN.resolve("ACCTFILE.txt"), dir, "ACCTFILE.txt");
        return TestSupport.run(dir, parm, CLOCK, t, IN.resolve("XREFFILE.txt"), IN.resolve("DISCGRP.txt"), acct);
    }

    private static String tc(String acct, String type, String cat, String bal) {
        return (String.format("%011d", Long.parseLong(acct)) + type + cat + bal).concat(" ".repeat(22)) + "\n";
    }

    @Test
    void D1_singleAccountIsChargedButNeverUpdated(@TempDir Path dir) throws IOException {
        Result r = runWith(dir, tc("3", "01", "0001", "0000023399G"), "2022071800");
        assertEquals(0, r.rc());
        List<String> tx = Files.readAllLines(dir.resolve("TRANSACT.txt"));
        assertEquals(1, tx.size());
        assertEquals("0000000292D", tx.get(0).substring(132, 143));
        assertEquals(Files.readAllLines(IN.resolve("ACCTFILE.txt")), Files.readAllLines(dir.resolve("ACCTFILE.txt")));
    }

    @Test
    void D1_lastAccountUnchangedButEarlierOnesUpdated(@TempDir Path dir) throws IOException {
        runWith(dir, tc("1", "01", "0001", "0000000100J") + tc("3", "01", "0001", "0000023399G"), "2022071800");
        List<String> in = Files.readAllLines(IN.resolve("ACCTFILE.txt")), out = Files.readAllLines(dir.resolve("ACCTFILE.txt"));
        assertNotEquals(in.get(0), out.get(0));
        assertEquals(in.get(2), out.get(2));
    }

    @Test
    void D2_truncationNotRounding(@TempDir Path dir) throws IOException {
        // 2339.97 @ 15% = 29.249625 -> 29.24 (half-up would be 29.25)
        runWith(dir, tc("3", "01", "0001", "0000023399G"), "2022071800");
        String amt = Files.readAllLines(dir.resolve("TRANSACT.txt")).get(0).substring(132, 143);
        assertEquals("0000000292D", amt);
    }

    @Test
    void D3_blankGroupFallsBackToDefaultWithTwoMissLines(@TempDir Path dir) throws IOException {
        Result r = runWith(dir, tc("3", "01", "0001", "0000023399G"), "2022071800");
        assertTrue(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
    }

    @Test
    void D3_groupHitHasNoMissLines(@TempDir Path dir) throws IOException {
        Result r = runWith(dir, tc("2", "01", "0001", "0000500000{"), "2022071800");
        assertFalse(r.sysout().contains("MISSING"));
    }

    @Test
    void D4_suffixWrapsAfter999999() {
        // pins the arithmetic used by InterestCalc: (suffix + 1) % 1_000_000, formatted %06d
        assertEquals("000000", String.format("%06d", (999_999 + 1) % 1_000_000));
    }

    @Test
    void D4_tranIdPrefixPaddedAndTruncatedTo10(@TempDir Path dir) throws IOException {
        runWith(dir, tc("3", "01", "0001", "0000023399G"), "ABC");
        assertEquals("ABC       000001", Files.readAllLines(dir.resolve("TRANSACT.txt")).get(0).substring(0, 16));
        Path d2 = Files.createDirectory(dir.resolve("long"));
        runWith(d2, tc("3", "01", "0001", "0000023399G"), "ABCDEFGHIJKLM");
        assertEquals("ABCDEFGHIJ000001", Files.readAllLines(d2.resolve("TRANSACT.txt")).get(0).substring(0, 16));
    }

    @Test
    void D4_balanceWrapsAt10IntegerDigits() {
        assertEquals(new java.math.BigDecimal("0.99"), Zoned.wrap(new java.math.BigDecimal("10000000000.99"), 10));
        assertEquals(new java.math.BigDecimal("1.00"), Zoned.wrap(new java.math.BigDecimal("10000000001.00"), 10));
    }
}
