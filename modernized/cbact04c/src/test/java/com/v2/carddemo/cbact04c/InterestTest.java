package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @TempDir Path tmp;

    /** One account per balance (rate 15.00 via DEFAULT group, as the group id is blank); returns TRANSACT amounts. */
    private List<String> amounts(String... bals) throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        String[] tc = new String[bals.length + 1];
        String[] ac = new String[bals.length + 1];
        String[] xr = new String[bals.length + 1];
        for (int i = 0; i <= bals.length; i++) {
            String id = String.format("%011d", i + 1);
            tc[i] = Fx.tcat(id, "01", "0001", i < bals.length ? bals[i] : "0000000000{");
            ac[i] = Fx.acct(id, Fx.BAL0, "");
            xr[i] = Fx.xref(String.format("%016d", i + 1), id);
        }
        Fx.write(in, "TCATBALF", tc);
        Fx.write(in, "ACCTFILE", ac);
        Fx.write(in, "XREFFILE", xr);
        Fx.write(in, "DISCGRP", Fx.disc("DEFAULT", "01", "0001", "00150{"));
        Fx.Run r = Fx.run(tmp.resolve("out"), in, tmp.resolve("nostock").toString());
        assertEquals(0, r.rc());
        return Files.readAllLines(r.out().resolve("TRANSACT.txt")).stream().map(l -> l.substring(132, 143)).toList();
    }

    @Test
    void truncatesNotRounds() throws IOException {
        // 1164.87 * 15.00 / 1200 = 14.56087... -> 14.56 (rounding would give the same; see next test)
        assertEquals(List.of("0000000145F"), amounts("0000011648G").subList(0, 1));
    }

    @Test
    void truncationDiffersFromRounding() throws IOException {
        // 100.07 * 15 / 1200 = 1.250875 -> 1.25 truncated, 1.25 rounded; 100.55 -> 1.256875 -> 1.25 (rounded 1.26)
        assertEquals(List.of("0000000012E"), amounts("0000001005E").subList(0, 1));
    }

    @Test
    void negativeBalancesTruncateTowardZero() throws IOException {
        // -100.00 -> -1.25 ; -1.27 -> -0.0158 -> -0.01
        assertEquals(List.of("0000000012N", "0000000000J"), amounts("0000001000}", "0000000012P").subList(0, 2));
    }

    @Test
    void overflowWrapsSilentlyD4() throws IOException {
        // 99,999,999.99 * 9999.99 / 1200 ~ 833,333,000 fits; use balance 999,999,999.99 so result exceeds 9 digits
        // 999999999.99 * 15 / 1200 = 12499999.99 -> fits. Rate 9999.99 pushes beyond 9 digits:
        Path in = tmp.resolve("in2");
        Files.createDirectories(in);
        Fx.write(in, "TCATBALF", Fx.tcat("00000000001", "01", "0001", "9999999999I"),
                Fx.tcat("00000000002", "01", "0001", "0000000000{"));
        Fx.write(in, "ACCTFILE", Fx.acct("00000000001", Fx.BAL0, ""), Fx.acct("00000000002", Fx.BAL0, ""));
        Fx.write(in, "XREFFILE", Fx.xref("0000000000000001", "00000000001"), Fx.xref("0000000000000002", "00000000002"));
        Fx.write(in, "DISCGRP", Fx.disc("DEFAULT", "01", "0001", "99999I"));
        Fx.Run r = Fx.run(tmp.resolve("out2"), in, tmp.resolve("nostock").toString());
        assertEquals(0, r.rc());
        // exact 8,333,324,999.99 exceeds 9 digits: high-order digit lost (D4)
        String amt = r.file("TRANSACT.txt").substring(132, 143);
        java.math.BigDecimal exact = new java.math.BigDecimal("999999999.99").multiply(new java.math.BigDecimal("9999.99"))
                .divide(new java.math.BigDecimal(1200), 2, java.math.RoundingMode.DOWN);
        assertEquals(Zoned.format(exact.remainder(java.math.BigDecimal.TEN.pow(9)), 11), amt);
    }

    @Test
    void scenario3HasFortyEightTruncatedAmounts() throws IOException {
        Path in = Fx.ROOT.resolve("legacy/golden/CBTRN02C/TCATBALF.txt");
        List<String> tcat = Files.readAllLines(in);
        List<String> disc = Files.readAllLines(Fx.ROOT.resolve("app/data/ASCII/discgrp.txt"));
        int truncated = 0;
        int nonZero = 0;
        for (String t : tcat) {
            String type = t.substring(11, 13);
            String cat = t.substring(13, 17);
            String d = disc.stream().filter(x -> x.startsWith("DEFAULT   " + type + cat)).findFirst().orElseThrow();
            java.math.BigDecimal rate = Zoned.parse(d.substring(16, 22));
            if (rate.signum() == 0) {
                continue;
            }
            nonZero++;
            java.math.BigDecimal q = Zoned.parse(t.substring(17, 28)).multiply(rate)
                    .divide(new java.math.BigDecimal(1200), 20, java.math.RoundingMode.DOWN);
            if (q.compareTo(q.setScale(2, java.math.RoundingMode.DOWN)) != 0) {
                truncated++;
            }
        }
        assertEquals(50, nonZero);
        assertEquals(48, truncated);
    }
}
