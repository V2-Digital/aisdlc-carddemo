package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GoldenTest {
    static final Path R = Fx.ROOT;

    private void stage(Path in, String dd, Path src) throws IOException {
        Files.copy(src, in.resolve(dd + ".txt"));
    }

    private void compare(Path out, String scenario, int expectedTrans) throws IOException {
        Path g = R.resolve("legacy/golden").resolve(scenario);
        for (String f : new String[] {"TRANSACT", "ACCTFILE", "SYSOUT"}) {
            Path o = out.resolve(f + ".txt");
            if (f.equals("SYSOUT")) {
                continue; // SYSOUT captured in Fx; compared separately
            }
            assertEquals(-1L, Files.mismatch(o, g.resolve(f + ".txt")), f + " differs from golden " + scenario);
        }
        assertEquals(expectedTrans, Files.readAllLines(out.resolve("TRANSACT.txt")).size());
        assertEquals("0", Files.readString(g.resolve("RC.txt")).trim());
    }

    @Test
    void stock() throws IOException {
        Path in = Files.createTempDirectory("in");
        Path a = R.resolve("app/data/ASCII");
        stage(in, "TCATBALF", a.resolve("tcatbal.txt"));
        stage(in, "XREFFILE", a.resolve("cardxref.txt"));
        stage(in, "ACCTFILE", a.resolve("acctdata.txt"));
        stage(in, "DISCGRP", a.resolve("discgrp.txt"));
        Fx fx = new Fx().run(in, Files.createTempDirectory("out"));
        assertEquals(0, fx.rc);
        compare(fx.out, "CBACT04C", 50);
        assertEquals(Files.readString(R.resolve("legacy/golden/CBACT04C/SYSOUT.txt")), fx.sysout);
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path in = Files.createTempDirectory("in");
        Path a = R.resolve("app/data/ASCII");
        Path g = R.resolve("legacy/golden/CBTRN02C");
        stage(in, "TCATBALF", g.resolve("TCATBALF.txt"));
        stage(in, "ACCTFILE", g.resolve("ACCTFILE.txt"));
        stage(in, "XREFFILE", a.resolve("cardxref.txt"));
        stage(in, "DISCGRP", a.resolve("discgrp.txt"));
        Fx fx = new Fx().run(in, Files.createTempDirectory("out"));
        assertEquals(0, fx.rc);
        compare(fx.out, "CBACT04C-after-CBTRN02C", 50);
        assertEquals(Files.readString(R.resolve("legacy/golden/CBACT04C-after-CBTRN02C/SYSOUT.txt")), fx.sysout);
        // 49 of 50 accounts changed (D1)
        var before = Files.readAllLines(g.resolve("ACCTFILE.txt"));
        var after = Files.readAllLines(fx.out.resolve("ACCTFILE.txt"));
        assertEquals(50, after.size());
        long changed = java.util.stream.IntStream.range(0, 50).filter(i -> !before.get(i).equals(after.get(i))).count();
        assertEquals(49, changed);
    }

    /** D2: every golden amount equals truncation; 28 of 50 would differ under HALF_UP. */
    @Test
    void goldenAmountsAreTruncationNotRounding() throws IOException {
        Path g = R.resolve("legacy/golden/CBTRN02C/TCATBALF.txt");
        var tcat = FixedRecords.read(g, 50);
        var out = FixedRecords.read(R.resolve("legacy/golden/CBACT04C-after-CBTRN02C/TRANSACT.txt"), 350);
        var rate = java.math.BigDecimal.valueOf(1500, 2);
        int i = 0;
        int differ = 0;
        for (String t : tcat) {
            if (!t.substring(11, 13).equals("01")) {
                continue;
            }
            var exact = Zoned.decode(t.substring(17, 28)).multiply(rate).divide(java.math.BigDecimal.valueOf(1200));
            var trunc = exact.setScale(2, java.math.RoundingMode.DOWN);
            var half = exact.setScale(2, java.math.RoundingMode.HALF_UP);
            String amt = out.get(i++).substring(132, 143);
            assertEquals(Zoned.encode(trunc, 11), amt);
            if (!trunc.equals(half)) {
                differ++;
            }
        }
        assertEquals(50, i);
        assertEquals(28, differ);
    }
}
