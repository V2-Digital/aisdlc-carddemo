package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy defects (preserved on purpose; see intent.md). */
class DefectCharacterizationTest {
    @TempDir Path dir;

    @Test void D1_lastAccountNotRewritten() throws IOException {
        Path g = Fx.ROOT.resolve("legacy/golden");
        Path in = g.resolve("CBTRN02C/ACCTFILE.txt");
        Path acct = Files.copy(in, dir.resolve("ACCTFILE"));
        Map<String, String> env = new HashMap<>();
        env.put("DD_TCATBALF", g.resolve("CBTRN02C/TCATBALF.txt").toString());
        env.put("DD_XREFFILE", Fx.ROOT.resolve("app/data/ASCII/cardxref.txt").toString());
        env.put("DD_DISCGRP", Fx.ROOT.resolve("app/data/ASCII/discgrp.txt").toString());
        env.put("DD_ACCTFILE", acct.toString());
        env.put("DD_TRANSACT", dir.resolve("TRANSACT").toString());
        env.put("CARDDEMO_CURRENT_DATE", Fx.FROZEN);
        Fx.Result r = Fx.exec(dir, env, "2022071800");
        List<String> before = Datasets.read(in, 300);
        assertEquals(before.get(49), r.accts().get(49), "acct 50 record unchanged");
        assertTrue(r.accts().get(49).startsWith("00000000050"));
        String tx = r.transact().get(49);
        assertTrue(tx.startsWith("2022071800000050"));
        assertEquals("Int. for a/c 00000000050", tx.substring(32, 56));
        assertEquals("0000000187G", tx.substring(132, 143)); // 18.77 still posted to TRANSACT
        for (int i = 0; i < 49; i++) {
            assertTrue(!before.get(i).equals(r.accts().get(i)), "acct " + (i + 1) + " rewritten");
        }
    }

    @Test void D2_interestTruncatedNotRounded() {
        // 14.560875 -> 14.56 (rounding would also give .56); 0.129 -> 0.12 proves no rounding
        assertEquals("0.12", Zoned.truncDivide(new java.math.BigDecimal("100.00").multiply(new java.math.BigDecimal("1.55")),
                java.math.BigDecimal.valueOf(1200)).toPlainString());
    }

    @Test void D3_noFeesCharged() throws IOException {
        // no txn other than type 01 / cat 0005 interest is ever written
        Fx.Result r = Fx.run(dir, List.of(Fx.tcat("00000000001", "01", "0001", "0000010000{"),
                        Fx.tcat("00000000002", "01", "0001", "0000010000{")),
                List.of(Fx.xref("00000000001", "1111111111111111"), Fx.xref("00000000002", "2222222222222222")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "00150{")),
                List.of(Fx.acct("00000000001", "00000000100{", ""), Fx.acct("00000000002", "00000000100{", "")),
                "2022071800", Fx.FROZEN);
        assertTrue(r.transact().stream().allMatch(t -> t.substring(16, 22).equals("010005")));
    }

    @Test void D7_discgrpOpenErrorSaysDalyRejects() throws IOException {
        Path d = dir;
        Fx.w(d, "T", List.of()); 
        Map<String, String> env = new HashMap<>();
        env.put("DD_TCATBALF", Fx.w(d, "TCATBALF", List.of()));
        env.put("DD_XREFFILE", Fx.w(d, "XREFFILE", List.of()));
        env.put("DD_ACCTFILE", Fx.w(d, "ACCTFILE", List.of()));
        env.put("DD_DISCGRP", d.resolve("nope").toString());
        Fx.Result r = Fx.exec(d, env, "2022071800");
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR OPENING DALY REJECTS FILE"));
    }
}
