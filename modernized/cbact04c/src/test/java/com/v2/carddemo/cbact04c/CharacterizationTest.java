package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins preserved legacy behaviour (spec section 6). */
class CharacterizationTest {
    @TempDir Path tmp;

    private InterestCalc.Result run(List<String> tcat, List<String> disc, List<String> acct) throws IOException {
        List<String> xref = List.of(Fx.xref(Fx.C1, Fx.A1), Fx.xref(Fx.C2, Fx.A2), Fx.xref(Fx.C3, Fx.A3));
        Fx.inputs(tmp, tcat, xref, disc, acct);
        return InterestCalc.run("2022071800", tmp, Fx.CLOCK);
    }

    private static final List<String> DEF = List.of(Fx.disc("DEFAULT", "01", "0001", "9999.99"),
            Fx.disc("DEFAULT", "03", "0001", "0"));

    /** P2: truncation toward zero, including negatives; HALF_UP would differ. */
    @Test void interestTruncatesTowardZero() throws IOException {
        InterestCalc.Result r = run(
                List.of(Fx.tcat(Fx.A1, "01", "0001", "999999999.99"), Fx.tcat(Fx.A2, "01", "0001", "-0.01"),
                        Fx.tcat(Fx.A3, "01", "0001", "-12.39")),
                DEF, List.of(Fx.acct(Fx.A1, "0", ""), Fx.acct(Fx.A2, "0", ""), Fx.acct(Fx.A3, "0", "")));
        assertEquals(3, r.transact().size());
        assertEquals("3333249999A", r.transact().get(0).substring(132, 143)); // wrapped (F5)
        assertEquals("0000000000Q", r.transact().get(1).substring(132, 143)); // -0.08
        assertEquals("0000001032M", r.transact().get(2).substring(132, 143)); // -103.24
        BigDecimal exact = new BigDecimal("-12.39").multiply(new BigDecimal("9999.99")).divide(BigDecimal.valueOf(1200));
        assertTrue(exact.setScale(2, RoundingMode.HALF_UP).compareTo(exact.setScale(2, RoundingMode.DOWN)) != 0);
    }

    /** P3: product truncates to zero but rate != 0, so a zero-amount tran is still written. */
    @Test void tinyNegativeWritesZeroAmountTran() throws IOException {
        InterestCalc.Result r = run(List.of(Fx.tcat(Fx.A1, "01", "0001", "-0.01"), Fx.tcat(Fx.A2, "03", "0001", "5")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "15"), Fx.disc("DEFAULT", "03", "0001", "0")),
                List.of(Fx.acct(Fx.A1, "0", ""), Fx.acct(Fx.A2, "0", "")));
        assertEquals("0000000000{", r.transact().get(0).substring(132, 143));
        assertEquals(1, r.transact().size());
    }

    /** F1: the last account in key order is never rewritten, though its tran is written. */
    @Test void lastAccountNotRewritten() throws IOException {
        String a1 = Fx.acct(Fx.A1, "100.00", ""), a2 = Fx.acct(Fx.A2, "100.00", "");
        InterestCalc.Result r = run(
                List.of(Fx.tcat(Fx.A1, "01", "0001", "1200"), Fx.tcat(Fx.A2, "01", "0001", "1200")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "10")), List.of(a1, a2));
        assertEquals(2, r.transact().size());
        assertEquals("0000000100{", r.transact().get(1).substring(132, 143));
        assertEquals(a2, r.accounts().get(1));
        assertEquals(Fx.z("110.00", 12), r.accounts().get(0).substring(12, 24));
    }

    /** F1 on real data: account 50 unchanged although tran 2022071800000050 charges 18.77. */
    @Test void lastAccountNotRewrittenGolden() throws IOException {
        Path g = Fx.repo().resolve("legacy/golden");
        List<String> pre = Files.readAllLines(g.resolve("CBTRN02C/ACCTFILE.txt"));
        List<String> post = Files.readAllLines(g.resolve("CBACT04C-after-CBTRN02C/ACCTFILE.txt"));
        assertEquals(pre.get(49), post.get(49));
        assertTrue(pre.get(49).startsWith("00000000050"));
        assertTrue(Files.readAllLines(g.resolve("CBACT04C-after-CBTRN02C/TRANSACT.txt")).get(49)
                .startsWith("2022071800000050"));
        assertEquals(49, java.util.stream.IntStream.range(0, 50).filter(i -> !pre.get(i).equals(post.get(i))).count());
    }

    /** Group-specific rate is used when present; otherwise DEFAULT with MISSING/TRY lines; rate 0 = no tran, cycles zeroed. */
    @Test void defaultFallbackAndZeroRate() throws IOException {
        InterestCalc.Result r = run(
                List.of(Fx.tcat(Fx.A1, "01", "0001", "1200"), Fx.tcat(Fx.A1, "03", "0001", "1200"),
                        Fx.tcat(Fx.A2, "01", "0001", "1200")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "10"), Fx.disc("DEFAULT", "03", "0001", "0")),
                List.of(Fx.acct(Fx.A1, "0", ""), Fx.acct(Fx.A2, "0", "")));
        assertEquals(2, r.transact().size()); // A1 01/0001 and A2 01/0001; A1 03/0001 has rate 0
        assertEquals(6, r.sysout().stream().filter(s -> s.startsWith("DISCLOSURE") || s.startsWith("TRY")).count());
        String acc = r.accounts().get(0);
        assertEquals("00000000000{", acc.substring(78, 90));
        assertEquals("00000000000{", acc.substring(90, 102));
        assertEquals(Fx.z("10.00", 12), acc.substring(12, 24));

        // group present -> no MISSING lines, group rate wins
        InterestCalc.Result g = run(List.of(Fx.tcat(Fx.A1, "01", "0001", "1200")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "10"), Fx.disc("GRP", "01", "0001", "20")),
                List.of(Fx.acct(Fx.A1, "0", "GRP")));
        assertTrue(g.sysout().stream().noneMatch(s -> s.startsWith("DISCLOSURE")));
        assertEquals("0000000200{", g.transact().get(0).substring(132, 143));
    }

    /** F4: timestamps from the clock, not the PARM date. */
    @Test void timestampFromClockNotParm() throws IOException {
        Fx.inputs(tmp, List.of(Fx.tcat(Fx.A1, "01", "0001", "1200")), List.of(Fx.xref(Fx.C1, Fx.A1)),
                List.of(Fx.disc("DEFAULT", "01", "0001", "10")), List.of(Fx.acct(Fx.A1, "0", "")));
        String t = InterestCalc.run("2020010100", tmp, () -> LocalDateTime.of(2031, 12, 25, 13, 14, 15, 160_000_000))
                .transact().get(0);
        assertTrue(t.startsWith("2020010100000001"));
        assertEquals("2031-12-25-13.14.15.160000", t.substring(278, 304));
        assertEquals(t.substring(278, 304), t.substring(304, 330));
    }

    /** F5: silent high-order truncation of balance, short PARM padding, suffix wrap. */
    @Test void overflowTruncatesHighOrder() throws IOException {
        InterestCalc.Result r = run(
                List.of(Fx.tcat(Fx.A1, "01", "0001", "1200"), Fx.tcat(Fx.A2, "01", "0001", "1")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "1.25")),
                List.of(Fx.acct(Fx.A1, "9999999999.99", ""), Fx.acct(Fx.A2, "0", "")));
        assertEquals("00000000012D", r.accounts().get(0).substring(12, 24)); // 1.24 (P6)
        Fx.inputs(tmp, List.of(Fx.tcat(Fx.A1, "01", "0001", "1")), List.of(Fx.xref(Fx.C1, Fx.A1)),
                List.of(Fx.disc("DEFAULT", "01", "0001", "1")), List.of(Fx.acct(Fx.A1, "0", "")));
        assertTrue(InterestCalc.run("AB", tmp, Fx.CLOCK).transact().get(0).startsWith("AB        000001"));
    }

    /** F7: legacy error texts are kept verbatim. */
    @Test void openErrorMessages() throws IOException {
        String[][] cases = {{"TCATBALF", "ERROR OPENING TRANSACTION CATEGORY BALANCE"},
                {"XREFFILE", "ERROR OPENING CROSS REF FILE35"}, {"DISCGRP", "ERROR OPENING DALY REJECTS FILE"},
                {"ACCTFILE", "ERROR OPENING ACCOUNT MASTER FILE"}};
        for (String[] c : cases) {
            Path d = Files.createTempDirectory(tmp, "o");
            Fx.inputs(d, List.of(), List.of(), List.of(), List.of());
            Files.delete(d.resolve(c[0] + ".txt"));
            InterestCalc.Result r = InterestCalc.run("2022071800", d, Fx.CLOCK);
            assertEquals(16, r.rc());
            assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C", c[1], "FILE STATUS IS: NNNN0035",
                    "ABENDING PROGRAM"), r.sysout());
        }
    }

    /** Input order is not trusted: TCATBALF is processed in key order. */
    @Test void unsortedTcatbalIsSorted() throws IOException {
        InterestCalc.Result r = run(
                List.of(Fx.tcat(Fx.A2, "01", "0001", "1200"), Fx.tcat(Fx.A1, "01", "0001", "1200")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "10")), List.of(Fx.acct(Fx.A1, "0", ""), Fx.acct(Fx.A2, "0", "")));
        assertTrue(r.transact().get(0).contains("a/c " + Fx.A1));
    }
}
