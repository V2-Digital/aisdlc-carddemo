package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: each pins legacy behaviour that must NOT be fixed in CR-1. */
class DefectTest {
    @TempDir Path tmp;

    private static final String A1 = "00000000001", A2 = "00000000002";
    private final List<String> xref = List.of(Support.xref("1111111111111111", A1), Support.xref("2222222222222222", A2));

    private Support.Result two(String rate, String group) throws IOException {
        return Support.run(tmp,
                List.of(Support.tcat(A1, "01", "0001", "1200.00"), Support.tcat(A2, "01", "0001", "2400.00")),
                xref,
                List.of(Support.account(A1, "100.00", "5.00", "6.00", group),
                        Support.account(A2, "200.00", "7.00", "8.00", group)),
                List.of(Support.disc("DEFAULT", "01", "0001", rate), Support.disc("GRP", "01", "0001", "24.00")));
    }

    @Test
    void lastAccountNotRewritten_D1() throws IOException {
        Support.Result r = two("12.00", "");
        assertEquals(2, r.transact().size());
        // account 1: interest 12.00 added, cycle fields zeroed
        assertEquals(Support.account(A1, "112.00", "0", "0", ""), r.acct().get(0));
        // account 2 (last): interest of 24.00 charged but record untouched
        assertEquals(Support.account(A2, "200.00", "7.00", "8.00", ""), r.acct().get(1));
        assertEquals(ZonedDecimal.encode(new java.math.BigDecimal("24.00"), 9, 2), r.transact().get(1).substring(132, 143));
    }

    @Test
    void fallsBackToDefaultOnMiss_D2() throws IOException {
        Support.Result r = two("12.00", "NOPE");
        assertEquals(2, r.sysout().split("DISCLOSURE GROUP RECORD MISSING").length - 1);
        assertEquals(2, r.sysout().split("TRY WITH DEFAULT GROUP CODE").length - 1);
        assertEquals(ZonedDecimal.encode(new java.math.BigDecimal("12.00"), 9, 2), r.transact().get(0).substring(132, 143));
    }

    @Test
    void realGroupHitDoesNotFallBack() throws IOException {
        Support.Result r = two("12.00", "GRP");
        assertFalse(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING"));
        assertEquals(ZonedDecimal.encode(new java.math.BigDecimal("24.00"), 9, 2), r.transact().get(0).substring(132, 143));
    }

    @Test
    void rateZeroWritesNoTransactionButAccountStillUpdated() throws IOException {
        Path d = tmp.resolve("z");
        Support.Result r = Support.run(d,
                List.of(Support.tcat(A1, "01", "0001", "1200.00"), Support.tcat(A2, "01", "0001", "2400.00")),
                xref,
                List.of(Support.account(A1, "100.00", "5.00", "6.00", ""), Support.account(A2, "200.00", "7.00", "8.00", "")),
                List.of(Support.disc("DEFAULT", "01", "0001", "0")));
        assertEquals(0, r.transact().size());
        assertEquals(Support.account(A1, "100.00", "0", "0", ""), r.acct().get(0));
    }

    @Test
    void transactionIdCounterRunsAcrossAccounts() throws IOException {
        Support.Result r = Support.run(tmp,
                List.of(Support.tcat(A1, "01", "0001", "10.00"), Support.tcat(A1, "01", "0002", "10.00"),
                        Support.tcat(A2, "01", "0001", "10.00")),
                xref,
                List.of(Support.account(A1, "1", "0", "0", ""), Support.account(A2, "1", "0", "0", "")),
                List.of(Support.disc("DEFAULT", "01", "0001", "12.00"), Support.disc("DEFAULT", "01", "0002", "12.00")));
        assertEquals(List.of("2022071800000001", "2022071800000002", "2022071800000003"),
                r.transact().stream().map(t -> t.substring(0, 16)).toList());
    }

    @Test
    void descAndFillerTailsAreSpaces() throws IOException {
        Support.Result r = two("12.00", "");
        String t = r.transact().get(0);
        assertEquals(350, t.length());
        assertEquals("Int. for a/c " + A1, t.substring(32, 56));
        assertEquals(" ".repeat(76), t.substring(56, 132));
        assertEquals(" ".repeat(20), t.substring(330));
        assertEquals("01" + "0005" + "System    ", t.substring(16, 32));
        assertEquals("2022-07-18-00.00.00.000000", t.substring(278, 304));
        assertEquals("1111111111111111", t.substring(262, 278));
    }

    @Test
    void accountWithoutCategoryRowsIsCopiedThrough() throws IOException {
        Path d = tmp.resolve("n");
        String a3 = Support.account("00000000003", "5.00", "1.00", "2.00", "");
        Support.Result r = Support.run(d,
                List.of(Support.tcat(A1, "01", "0001", "10.00"), Support.tcat(A2, "01", "0001", "10.00")),
                xref,
                List.of(Support.account(A1, "1", "0", "0", ""), Support.account(A2, "1", "0", "0", ""), a3),
                List.of(Support.disc("DEFAULT", "01", "0001", "12.00")));
        assertEquals(a3, r.acct().get(2));
    }

    @Test
    void emptyTcatbalLeavesEverythingUntouched() throws IOException {
        String a = Support.account(A1, "1.00", "2.00", "3.00", "");
        Support.Result r = Support.run(tmp, List.of(), xref, List.of(a), List.of(Support.disc("DEFAULT", "01", "0001", "1")));
        assertEquals(0, r.rc());
        assertEquals(0, r.transact().size());
        assertEquals(List.of(a), r.acct());
    }

    @Test
    void realGoldenLastAccount050() throws IOException {
        Path g = Support.ROOT.resolve("legacy/golden");
        List<String> after = Files.readAllLines(g.resolve("CBACT04C-after-CBTRN02C/ACCTFILE.txt"));
        List<String> before = Files.readAllLines(g.resolve("CBTRN02C/ACCTFILE.txt"));
        assertEquals(before.get(49), after.get(49));
        assertNotEquals(before.get(0), after.get(0));
        List<String> tx = Files.readAllLines(g.resolve("CBACT04C-after-CBTRN02C/TRANSACT.txt"));
        assertTrue(tx.get(49).startsWith("2022071800000050"));
        assertEquals("0000000187G", tx.get(49).substring(132, 143));
    }
}
