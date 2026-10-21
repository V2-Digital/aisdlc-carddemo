package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: each pins a legacy defect that must NOT be fixed in CR-1. */
class DefectTest {
    @TempDir Path tmp;

    private Path in() throws IOException {
        return Fx.stockInputs(Files.createDirectory(tmp.resolve("in")));
    }

    private static String acct(List<String> accts, String id) {
        return accts.stream().filter(l -> l.startsWith(id)).findFirst().orElseThrow();
    }

    @Test
    void d1_lastAccountNotRewritten_butItsTxnIsWritten() throws IOException {
        Path in = in();
        Fx.put(in, "TCATBALF", List.of(Fx.tcat("00000000001", "01", "0001", "00000100000"),
            Fx.tcat("00000000002", "01", "0001", "00000100000")));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        List<String> before = Fx.lines(in.resolve("ACCTFILE.txt"));
        List<String> after = Fx.lines(r.out().resolve("ACCTFILE.txt"));
        List<String> tx = Fx.lines(r.out().resolve("TRANSACT.txt"));
        assertEquals(2, tx.size());
        assertTrue(tx.get(1).contains("Int. for a/c 00000000002"));
        assertNotEquals(acct(before, "00000000001"), acct(after, "00000000001"));
        assertEquals(acct(before, "00000000002"), acct(after, "00000000002"));
        assertEquals(before.size(), after.size());
    }

    @Test
    void d1_singleAccountRunRewritesNothing() throws IOException {
        Path in = in();
        Fx.put(in, "TCATBALF", List.of(Fx.tcat("00000000001", "01", "0001", "00000100000")));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(Files.readString(in.resolve("ACCTFILE.txt")), Files.readString(r.out().resolve("ACCTFILE.txt")));
        assertEquals(1, Fx.lines(r.out().resolve("TRANSACT.txt")).size());
    }

    @Test
    void d3_cycleZeroedEvenWithoutInterest() throws IOException {
        Path in = in();
        // rate 0 for 03/0001 DEFAULT (stock): no txn, but acct 1 is still rewritten (cycle fields zeroed)
        List<String> accts = Fx.lines(in.resolve("ACCTFILE.txt"));
        accts.set(0, Fx.lineWith(accts.get(0), 78, "0000000500A0000000300B"));
        Fx.put(in, "ACCTFILE", accts);
        Fx.put(in, "TCATBALF", List.of(Fx.tcat("00000000001", "03", "0001", "00000100000"),
            Fx.tcat("00000000002", "03", "0001", "00000100000")));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(0, Fx.lines(r.out().resolve("TRANSACT.txt")).size());
        String a = acct(Fx.lines(r.out().resolve("ACCTFILE.txt")), "00000000001");
        String orig = acct(Fx.lines(in.resolve("ACCTFILE.txt")), "00000000001");
        assertEquals("00000000000{", a.substring(78, 90));
        assertEquals("00000000000{", a.substring(90, 102));
        assertNotEquals(orig.substring(78, 102), a.substring(78, 102), "fixture must have non-zero cycle fields");
        assertEquals(orig.substring(0, 78), a.substring(0, 78));
    }

    @Test
    void d4_negativeBalanceGivesNegativeInterest() throws IOException {
        Path in = in();
        // balance -1000.00 ("0000010000}") * 15 / 1200 = -12.50 -> credit txn
        Fx.put(in, "TCATBALF", List.of(Fx.tcat("00000000001", "01", "0001", "0000010000}"),
            Fx.tcat("00000000002", "01", "0001", "0000000000{")));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        String t = Fx.lines(r.out().resolve("TRANSACT.txt")).get(0);
        assertEquals("0000000125}", t.substring(132, 143));
        // account 1 balance is reduced by 12.50
        String a = acct(Fx.lines(r.out().resolve("ACCTFILE.txt")), "00000000001");
        String o = acct(Fx.lines(in.resolve("ACCTFILE.txt")), "00000000001");
        assertEquals(Zoned.parse(o.substring(12, 24), 2).subtract(new java.math.BigDecimal("12.50")),
            Zoned.parse(a.substring(12, 24), 2));
    }

    @Test
    void d5_discgrpOpenFailureText() throws IOException {
        Path in = in();
        Files.delete(in.resolve("DISCGRP.txt"));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR OPENING DALY REJECTS FILE"));
        assertTrue(r.syserr().contains("CEE3ABD: ABEND U0999"));
    }
}
