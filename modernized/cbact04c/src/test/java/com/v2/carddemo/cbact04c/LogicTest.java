package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Interest arithmetic, transaction ids, DEFAULT fallback, and characterization of defects D1-D4. */
class LogicTest {
    @TempDir Path tmp;
    Path in, out;

    private T.Result run(List<String> tcat, List<String> accts, List<String> xrefs, List<String> disc) throws Exception {
        in = tmp.resolve("in");
        out = tmp.resolve("out");
        Files.createDirectories(in);
        T.put(in, "TCATBALF", tcat.toArray(String[]::new));
        T.put(in, "ACCTFILE", accts.toArray(String[]::new));
        T.put(in, "XREFFILE", xrefs.toArray(String[]::new));
        T.put(in, "DISCGRP", disc.toArray(String[]::new));
        return T.run("2022071800", in, out);
    }

    private String amount(String tran) {
        return Overpunch.decode(tran.substring(132, 143), 2).toPlainString();
    }

    private static final String A1 = "00000000001", A2 = "00000000002", A3 = "00000000003";
    private List<String> xrefs() {
        return List.of(T.xref("1111111111111111", A1), T.xref("2222222222222222", A2), T.xref("3333333333333333", A3));
    }
    private List<String> accts() {
        return List.of(T.acct(A1, "100.00", "5.00", "7.00", ""), T.acct(A2, "200.00", "5.00", "7.00", ""),
                T.acct(A3, "300.00", "5.00", "7.00", ""));
    }
    private List<String> disc() {
        return List.of(T.disc("DEFAULT", "01", "0001", "15.00"), T.disc("DEFAULT", "02", "0001", "0"),
                T.disc("A000000000", "01", "0001", "99.00"));
    }

    // ---- InterestTest
    @Test
    void truncatesTowardZeroPositive() throws Exception {
        // 1164.87 * 15 / 1200 = 14.560875 -> 14.56 (HALF_UP would also give 14.56)
        run(List.of(T.tcat(A1, "01", "0001", "1164.87"), T.tcat(A2, "01", "0001", "0")), accts(), xrefs(), disc());
        assertEquals("14.56", amount(T.read(out.resolve("TRANSACT.txt"), 350).get(0)));
    }

    @Test
    void truncationDiffersFromRounding_D3() throws Exception {
        // 100.07 * 15 / 1200 = 1.250875 -> truncated 1.25 ; 100.15*15/1200 = 1.251875.. use 100.47: 1.255875 -> 1.25 (HALF_UP 1.26)
        run(List.of(T.tcat(A1, "01", "0001", "100.47"), T.tcat(A2, "01", "0001", "0")), accts(), xrefs(), disc());
        assertEquals("1.25", amount(T.read(out.resolve("TRANSACT.txt"), 350).get(0)));
    }

    @Test
    void negativeBalanceTruncatesTowardZero() throws Exception {
        // -10.01 * 15 / 1200 = -0.125125 -> -0.12 (floor would give -0.13), encoded ...1K
        run(List.of(T.tcat(A1, "01", "0001", "-10.01"), T.tcat(A2, "01", "0001", "0")), accts(), xrefs(), disc());
        String t = T.read(out.resolve("TRANSACT.txt"), 350).get(0);
        assertEquals("-0.12", amount(t));
        assertEquals("00000000001K", "0" + t.substring(132, 143));
    }

    @Test
    void accountRewrittenWithBalancePlusTotalAndCyclesZeroed() throws Exception {
        run(List.of(T.tcat(A1, "01", "0001", "1200.00"), T.tcat(A1, "02", "0001", "500.00"),
                T.tcat(A2, "01", "0001", "0")), accts(), xrefs(), disc());
        String a1 = T.read(out.resolve("ACCTFILE.txt"), 300).get(0);
        assertEquals(Overpunch.encode(new java.math.BigDecimal("115.00"), 2, 12), a1.substring(12, 24)); // 100 + 15.00
        assertEquals("00000000000{", a1.substring(78, 90));
        assertEquals("00000000000{", a1.substring(90, 102));
        assertEquals(accts().get(0).substring(24, 78), a1.substring(24, 78)); // untouched bytes pass through
        assertEquals(accts().get(0).substring(102), a1.substring(102));
    }

    // ---- TranIdTest
    @Test
    void idsSequentialAcrossAccountsZeroAmountCountsRateZeroDoesNot() throws Exception {
        run(List.of(T.tcat(A1, "01", "0001", "0"),      // rate 15, zero amount -> txn 000001
                T.tcat(A1, "02", "0001", "50.00"),      // rate 0 -> no txn, no id
                T.tcat(A2, "01", "0001", "120.00"),     // txn 000002
                T.tcat(A3, "01", "0001", "10.00")), accts(), xrefs(), disc());
        List<String> t = T.read(out.resolve("TRANSACT.txt"), 350);
        assertEquals(3, t.size());
        assertEquals("2022071800000001", t.get(0).substring(0, 16));
        assertEquals("0.00", amount(t.get(0)));
        assertEquals("2022071800000002", t.get(1).substring(0, 16));
        assertEquals("2022071800000003", t.get(2).substring(0, 16));
        assertEquals("Int. for a/c " + A2, t.get(1).substring(32, 56));
        assertEquals("2222222222222222", t.get(1).substring(262, 278));
    }

    // ---- DefaultFallbackTest
    @Test
    void groupMissFallsBackToDefaultForSameTypeCat() throws Exception {
        List<String> accts = List.of(T.acct(A1, "100.00", "0", "0", "GRPX"), T.acct(A2, "0", "0", "0", ""));
        T.Result r = run(List.of(T.tcat(A1, "01", "0001", "1200.00"), T.tcat(A2, "01", "0001", "0")), accts, xrefs(), disc());
        assertEquals(0, r.rc());
        assertEquals("15.00", amount(T.read(out.resolve("TRANSACT.txt"), 350).get(0)));
        assertTrue(r.out().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
    }

    @Test
    void groupHitDoesNotFallBack() throws Exception {
        List<String> accts = List.of(T.acct(A1, "100.00", "0", "0", "A000000000"), T.acct(A2, "0", "0", "0", ""));
        T.Result r = run(List.of(T.tcat(A1, "01", "0001", "1200.00"), T.tcat(A2, "02", "0001", "0")), accts, xrefs(), disc());
        assertEquals("99.00", amount(T.read(out.resolve("TRANSACT.txt"), 350).get(0)));
        assertFalse(r.out().split("\n")[2].contains("MISSING"));
    }

    // ---- Characterization
    @Test
    void D1_lastAccountNeverRewrittenButItsTransactionIsWritten() throws Exception {
        run(List.of(T.tcat(A1, "01", "0001", "1200.00"), T.tcat(A2, "01", "0001", "1200.00")), accts(), xrefs(), disc());
        List<String> t = T.read(out.resolve("TRANSACT.txt"), 350);
        List<String> a = T.read(out.resolve("ACCTFILE.txt"), 300);
        assertEquals(2, t.size());
        assertEquals("15.00", amount(t.get(1)));          // interest charged to account 2 ...
        assertEquals(accts().get(1), a.get(1));           // ... but its record is byte-identical to the input
        assertNotEquals(accts().get(0), a.get(0));        // account 1 was rewritten
    }

    @Test
    void D1_onGoldenData_account50UnchangedWhileTransaction50Exists() throws Exception {
        String g = Files.readAllLines(T.GOLDEN.resolve("CBACT04C-after-CBTRN02C/ACCTFILE.txt")).get(49);
        String before = Files.readAllLines(T.GOLDEN.resolve("CBTRN02C/ACCTFILE.txt")).get(49);
        assertEquals(before, g);
        String tran = Files.readAllLines(T.GOLDEN.resolve("CBACT04C-after-CBTRN02C/TRANSACT.txt")).get(49);
        assertTrue(tran.startsWith("2022071800000050"));
        assertEquals("18.77", amount(tran));
    }

    @Test
    void D2_blankGroupIdMeansGroupRatesNeverUsedInSampleData() throws Exception {
        for (String a : Files.readAllLines(T.ASCII.resolve("acctdata.txt"))) {
            assertEquals("          ", FixedFile.pad(a, 300).substring(112, 122));
        }
        assertTrue(Files.readString(T.GOLDEN.resolve("CBACT04C/SYSOUT.txt")).lines()
                .filter(l -> l.equals("DISCLOSURE GROUP RECORD MISSING")).count() == 50);
    }

    @Test
    void D4_emptyFeesAndZeroAmountTransactionsOnStockData() throws Exception {
        List<String> t = Files.readAllLines(T.GOLDEN.resolve("CBACT04C/TRANSACT.txt"));
        assertEquals(50, t.size());
        assertTrue(t.stream().allMatch(x -> amount(x).equals("0.00")));
    }

    @Test
    void D4_counterIsSixDigitSequence_wrapNotExercised() throws Exception {
        // 1,000,001 non-zero-rate rows in one account is heavy; verify via 3 rows that the suffix is %06d (wrap is mod 10^6 in code)
        run(List.of(T.tcat(A1, "01", "0001", "1.00"), T.tcat(A2, "01", "0001", "1.00")), accts(), xrefs(), disc());
        assertEquals("2022071800000002", T.read(out.resolve("TRANSACT.txt"), 350).get(1).substring(0, 16));
    }

    @Test
    void emptyTcatbalLeavesAccountsUnchanged() throws Exception {
        T.Result r = run(List.of(), accts(), xrefs(), disc());
        assertEquals(0, r.rc());
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n", r.out());
        assertEquals(0, Files.size(out.resolve("TRANSACT.txt")));
        assertEquals(accts(), T.read(out.resolve("ACCTFILE.txt"), 300));
    }

    @Test
    void duplicateXrefUsesLowestCardNumber() throws Exception {
        List<String> x = List.of(T.xref("9999999999999999", A1), T.xref("1111111111111111", A1),
                T.xref("2222222222222222", A2));
        run(List.of(T.tcat(A1, "01", "0001", "1.00"), T.tcat(A2, "01", "0001", "1.00")), accts(), x, disc());
        assertEquals("1111111111111111", T.read(out.resolve("TRANSACT.txt"), 350).get(0).substring(262, 278));
    }
}
