package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy behaviour (including defects) of CBACT04C. Do not "fix" these without a separate CR. */
class CharacterizationTest {
    static final String A1 = "00000000001";
    static final String A2 = "00000000002";
    static final String BAL1 = "0000001004{"; // 100.40 as S9(9)V99

    private Path base(Path tmp, String group, String tcat1Type) throws IOException {
        Path in = tmp.resolve("in");
        put(in, "TCATBALF", tcat(A1, tcat1Type, "0001", BAL1), tcat(A2, "01", "0001", BAL1));
        put(in, "XREFFILE", xref("1111222233334444", A1), xref("5555666677778888", A2));
        put(in, "ACCTFILE", acct(A1, "00000010000{", group, "00000000100{", "00000000200{"),
                acct(A2, "00000020000{", group, "00000000100{", "00000000200{"));
        put(in, "DISCGRP", disc("DEFAULT", "01", "0001", "00150{"), disc("DEFAULT", "03", "0001", "00000{"));
        return in;
    }

    @Test
    void lastAccountNotRewritten_D1(@TempDir Path tmp) throws IOException {
        Path in = base(tmp, "", "01");
        Result r = run(in, tmp.resolve("out"));
        List<String> in_ = Files.readAllLines(in.resolve("ACCTFILE.txt"));
        List<String> out = Files.readAllLines(r.out().resolve("ACCTFILE.txt"));
        assertNotEquals(in_.get(0), out.get(0), "first account is rewritten");
        assertEquals("00000010012E", out.get(0).substring(12, 24), "1000.00 + 1.25");
        assertEquals(in_.get(1), out.get(1), "last account unchanged (lost interest + cycle reset)");
        List<String> tran = Files.readAllLines(r.out().resolve("TRANSACT.txt"));
        assertEquals(2, tran.size());
        assertEquals("0000000012E", tran.get(1).substring(InterestTest.AMT, InterestTest.AMT + 11), "still charged");
    }

    @Test
    void defaultGroupFallback_D2(@TempDir Path tmp) throws IOException {
        Result r = run(base(tmp, "", "01"), tmp.resolve("out"));
        assertEquals(0, r.rc());
        assertEquals(2, r.sysout().split("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n", -1).length - 1);
    }

    @Test
    void knownGroupNeedsNoFallback(@TempDir Path tmp) throws IOException {
        Path in = base(tmp, "G1", "01");
        put(in, "DISCGRP", disc("G1", "01", "0001", "00150{"), disc("DEFAULT", "01", "0001", "00150{"));
        Result r = run(in, tmp.resolve("out"));
        assertEquals(0, r.rc());
        assertFalse(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING"));
    }

    @Test
    void missingDefaultAbends(@TempDir Path tmp) throws IOException {
        Path in = base(tmp, "", "01");
        put(in, "DISCGRP", disc("DEFAULT", "03", "0001", "00000{"));
        Result r = run(in, tmp.resolve("out"));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
        assertFalse(r.sysout().contains("END OF EXECUTION"));
    }

    @Test
    void zeroRateNoTranButCycleZeroed(@TempDir Path tmp) throws IOException {
        Result r = run(base(tmp, "", "03"), tmp.resolve("out"));
        assertEquals(0, r.rc());
        assertEquals(1, Files.readAllLines(r.out().resolve("TRANSACT.txt")).size(), "only account 2 is charged");
        String a1 = Files.readAllLines(r.out().resolve("ACCTFILE.txt")).get(0);
        assertEquals("00000010000{", a1.substring(12, 24), "balance unchanged: no interest");
        assertEquals(Z12, a1.substring(78, 90));
        assertEquals(Z12, a1.substring(90, 102));
    }

    @Test
    void missingAccount(@TempDir Path tmp) throws IOException {
        Path in = base(tmp, "", "01");
        put(in, "ACCTFILE", acct(A2, Z12, "", Z12, Z12));
        Result r = run(in, tmp.resolve("out"));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: " + A1 + "\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
    }

    @Test
    void missingXref(@TempDir Path tmp) throws IOException {
        Path in = base(tmp, "", "01");
        put(in, "XREFFILE", xref("5555666677778888", A2));
        Result r = run(in, tmp.resolve("out"));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: " + A1 + "\nERROR READING XREF FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
    }

    @Test
    void abendKeepsPartialOutput_D4(@TempDir Path tmp) throws IOException {
        Path in = base(tmp, "", "01");
        put(in, "TCATBALF", tcat(A1, "01", "0001", BAL1), tcat(A2, "01", "0001", BAL1), tcat("00000000003", "01", "0001", BAL1));
        Result r = run(in, tmp.resolve("out"));
        assertEquals(16, r.rc());
        assertEquals(2, Files.readAllLines(r.out().resolve("TRANSACT.txt")).size());
        assertNotEquals(Files.readAllLines(in.resolve("ACCTFILE.txt")).get(0),
                Files.readAllLines(r.out().resolve("ACCTFILE.txt")).get(0), "rewrite before abend is kept");
    }

    @Test
    void openErrorMessageSaysDalyRejects_D5(@TempDir Path tmp) throws IOException {
        Path in = base(tmp, "", "01");
        Files.delete(in.resolve("DISCGRP.txt"));
        Result r = run(in, tmp.resolve("out"));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR OPENING DALY REJECTS FILE\nFILE STATUS IS: NNNN0035\n"));
    }

    @Test
    void balanceOverflowDropsHighDigits_D6(@TempDir Path tmp) throws IOException {
        Path in = base(tmp, "", "01");
        put(in, "ACCTFILE", acct(A1, "99999999999{", "", Z12, Z12), acct(A2, Z12, "", Z12, Z12));
        Result r = run(in, tmp.resolve("out"));
        // 9999999999.90 + 1.25 = 10000000001.15 -> high digit dropped
        String bal = Files.readAllLines(r.out().resolve("ACCTFILE.txt")).get(0).substring(12, 24);
        assertEquals("00000000011E", bal);
    }

    @Test
    void firstXrefCardWins_D7(@TempDir Path tmp) throws IOException {
        Path in = base(tmp, "", "01");
        put(in, "XREFFILE", xref("1111222233334444", A1), xref("9999999999999999", A1), xref("5555666677778888", A2));
        Result r = run(in, tmp.resolve("out"));
        String card = Files.readAllLines(r.out().resolve("TRANSACT.txt")).get(0).substring(262, 278);
        assertEquals("1111222233334444", card);
    }
}
