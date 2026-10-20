package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Acceptance-criteria tests on golden data, pinning preserved legacy defects. */
class CharacterizationTest {
    static final Path GOLDEN = Path.of("../../legacy/golden");
    static final Path SAMPLE = Path.of("../../app/data/ASCII");

    @TempDir
    Path tmp;

    String sysout;

    Path runAfter() throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Files.copy(GOLDEN.resolve("CBTRN02C/TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(SAMPLE.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(SAMPLE.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        int rc = new Cbact04c(in, tmp.resolve("out"), "2022071800", Main.clock("2022071800000000"),
                new PrintStream(so, true, StandardCharsets.ISO_8859_1), System.err).run();
        assertEquals(0, rc);
        sysout = so.toString(StandardCharsets.ISO_8859_1);
        return tmp.resolve("out");
    }

    static String line(Path f, String prefix) throws IOException {
        return Files.readAllLines(f, StandardCharsets.ISO_8859_1).stream().filter(l -> l.startsWith(prefix))
                .findFirst().orElseThrow();
    }

    @Test
    void interestTruncatesTowardZero_acct2_29_24() throws IOException {
        Path out = runAfter();
        // 2339.97 * 15 / 1200 = 29.249625 -> 29.24 (a rounding rewrite would give 29.25)
        String tran = line(out.resolve("TRANSACT.txt"), "2022071800000002");
        assertEquals("0000000292D", tran.substring(132, 143));
    }

    @Test
    void defectF1_lastAccountNotRewritten_acct50() throws IOException {
        Path out = runAfter();
        String tran = line(out.resolve("TRANSACT.txt"), "2022071800000050");
        assertTrue(tran.contains("Int. for a/c 00000000050"));
        assertEquals(line(GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"), "00000000050"),
                line(out.resolve("ACCTFILE.txt"), "00000000050"));
        // ...while a non-last account (49) is rewritten
        assertTrue(!line(GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"), "00000000049")
                .equals(line(out.resolve("ACCTFILE.txt"), "00000000049")));
    }

    @Test
    void defectF3_everyRateLookupFallsBackToDefault() throws IOException {
        runAfter();
        long misses = sysout.lines().filter("DISCLOSURE GROUP RECORD MISSING"::equals).count();
        assertEquals(100, misses);
        assertEquals(100, sysout.lines().filter("TRY WITH DEFAULT GROUP CODE"::equals).count());
    }

    @Test
    void rateZero_noTransactionButAccountsStillRewritten() throws IOException {
        Path out = runAfter();
        // 100 TCATBAL records, type 03 has rate 0 -> only 50 transactions
        assertEquals(50, Files.readAllLines(out.resolve("TRANSACT.txt")).size());
        // non-last accounts: cycle credit/debit zeroed
        String a1 = line(out.resolve("ACCTFILE.txt"), "00000000001");
        assertEquals("{".repeat(1), a1.substring(89, 90));
        assertEquals("000000000000".substring(0, 11) + "{", a1.substring(78, 90));
        assertEquals("000000000000".substring(0, 11) + "{", a1.substring(90, 102));
    }

    @Test
    void defectF2_F5_arithmeticUnits() {
        assertEquals(new BigDecimal("-29.24"), new BigDecimal("-2339.97").multiply(new BigDecimal("15"))
                .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.DOWN));
        assertEquals(new BigDecimal("1.23"), Zoned.truncate(new BigDecimal("1000000001.23"), 9));
        assertEquals(new BigDecimal("-1.23"), Zoned.truncate(new BigDecimal("-1000000001.23"), 9));
    }

    @Test
    void overpunch_allTwentySignCharsRoundTrip() {
        String pos = "{ABCDEFGHI";
        String neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            BigDecimal p = new BigDecimal(d).movePointLeft(2).setScale(2);
            assertEquals("0000000000" + pos.charAt(d), Zoned.encode(p, 11));
            assertEquals(p, Zoned.decode("0000000000" + pos.charAt(d), 2));
            // -0 reads back as 0; non-zero negatives round-trip
            BigDecimal n = p.negate();
            if (d > 0) {
                assertEquals("0000000000" + neg.charAt(d), Zoned.encode(n, 11));
            }
            assertEquals(n.setScale(2), Zoned.decode("0000000000" + neg.charAt(d), 2).setScale(2));
        }
        assertEquals("0000000000}", "0000000000" + neg.charAt(0));
        assertEquals(List.of('{', '}'), List.of(pos.charAt(0), neg.charAt(0)));
    }
}
