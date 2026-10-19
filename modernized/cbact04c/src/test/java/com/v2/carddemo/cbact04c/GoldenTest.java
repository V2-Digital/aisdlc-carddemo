package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenTest {
    private static List<String> rd(Path p, int len) throws Exception { return FileStore.read(p, len); }

    private void check(Path acctIn, Path tcatIn, String golden) throws Exception {
        Path app = Fx.repo().resolve("app/data/ASCII");
        Path g = Fx.repo().resolve("legacy/golden").resolve(golden);
        List<String> sys = new ArrayList<>();
        InterestCalc c = new InterestCalc("2022071800", Fx.CLOCK, sys::add, rd(tcatIn, 50),
                rd(app.resolve("cardxref.txt"), 50), rd(app.resolve("discgrp.txt"), 50), rd(acctIn, 300));
        c.run(); // no abend => RC 0
        assertEquals("0", Files.readString(g.resolve("RC.txt")).trim());
        Path tmp = Files.createTempDirectory("gt");
        FileStore.write(tmp.resolve("TRANSACT.txt"), c.transactions);
        FileStore.write(tmp.resolve("ACCTFILE.txt"), c.accounts);
        assertEquals(50, c.transactions.size());
        assertEquals(50, c.accounts.size());
        for (String f : List.of("TRANSACT.txt", "ACCTFILE.txt")) {
            assertArrayEquals(Files.readAllBytes(g.resolve(f)), Files.readAllBytes(tmp.resolve(f)), f);
        }
        // SYSOUT is informational only (Q1): report, don't gate
        String exp = Files.readString(g.resolve("SYSOUT.txt"));
        String got = String.join("\n", sys) + "\n";
        System.out.println("SYSOUT " + golden + " identical=" + exp.equals(got));
    }

    @Test void stock() throws Exception {
        Path app = Fx.repo().resolve("app/data/ASCII");
        check(app.resolve("acctdata.txt"), app.resolve("tcatbal.txt"), "CBACT04C");
    }

    @Test void afterCbtrn02c() throws Exception {
        Path g = Fx.repo().resolve("legacy/golden/CBTRN02C");
        check(g.resolve("ACCTFILE.txt"), g.resolve("TCATBALF.txt"), "CBACT04C-after-CBTRN02C");
    }

    /** AC5: 28 of the 50 golden amounts would differ if rounded instead of truncated. */
    @Test void truncatesNotRounds() throws Exception {
        Path g = Fx.repo().resolve("legacy/golden/CBTRN02C");
        Path app = Fx.repo().resolve("app/data/ASCII");
        List<String> disc = rd(app.resolve("discgrp.txt"), 50);
        int differ = 0;
        for (String t : rd(g.resolve("TCATBALF.txt"), 50)) {
            long bal = Records.tcatBal(t);
            for (String d : disc) {
                if (d.startsWith("DEFAULT") && Records.discKey(d).endsWith(Records.tcatType(t) + Records.tcatCat(t))) {
                    long rate = Records.discRate(d);
                    if (rate == 0) continue;
                    long num = bal * rate;
                    long trunc = num / 120000;
                    long rounded = Math.floorDiv(num + 60000, 120000);
                    if (trunc != rounded) differ++;
                }
            }
        }
        assertEquals(28, differ);
    }
}
