package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CharacterizationTest {
    @TempDir Path tmp;

    private static final String A1 = Fx.acct("00000000001", "00000100000{", "A");
    private static final String A2 = Fx.acct("00000000002", "00000200000{", "A");

    @Test
    void lastAccountNotRewritten() throws Exception { // D1
        Fx f = new Fx(tmp);
        f.base(List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{"),
                Fx.tcat("00000000002", "01", "0001", "0000012000{")), List.of(A1, A2));
        assertEquals(0, f.run("2022071800"));
        String[] txn = f.read("TRANSACT.txt").split("\n");
        assertEquals(2, txn.length);
        assertTrue(txn[0].startsWith("2022071800000001") && txn[1].startsWith("2022071800000002"));
        String[] acc = f.read("ACCTFILE.txt").split("\n");
        assertNotEquals(A1, acc[0]);
        assertEquals("00000100150{", acc[0].substring(12, 24)); // 10000.00 + 15.00
        assertEquals("00000000000{", acc[0].substring(78, 90));
        assertEquals(A2, acc[1]); // second account has a txn but is untouched
    }

    @Test
    void defaultFallback() throws Exception { // D4
        Fx f = new Fx(tmp);
        f.base(List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{")), List.of(A1, A2));
        assertEquals(0, f.run("2022071800"));
        assertTrue(f.out().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        // specific group present: no fallback message
        f.write("DISCGRP.txt", List.of(Fx.disc("A         ", "01", "0001", "00100{"), Fx.disc("DEFAULT   ", "01", "0001", "00150{")));
        f.sysout.reset();
        assertEquals(0, f.run("2022071800"));
        assertFalse(f.out().contains("MISSING"));
        assertEquals("0000000100{", f.read("TRANSACT.txt").substring(132, 143)); // 1200.00 * 10.00% / 12 = 10.00
    }

    private void assertAbend(Fx f, String... lines) {
        assertEquals(16, f.run("2022071800"));
        for (String l : lines) assertTrue(f.out().contains(l), l + "\n" + f.out());
        assertTrue(f.out().contains("ABENDING PROGRAM"));
        assertTrue(f.syserr.toString().contains("CEE3ABD: ABEND U0999"));
        assertFalse(Files.exists(tmp.resolve("TRANSACT.txt")), "no partial TRANSACT");
    }

    @Test
    void abendMissingAccount() throws Exception {
        Fx f = new Fx(tmp);
        f.base(List.of(Fx.tcat("00000000009", "01", "0001", "0000012000{")), List.of(A1));
        assertAbend(f, "ACCOUNT NOT FOUND: 00000000009", "ERROR READING ACCOUNT FILE", "FILE STATUS IS: NNNN0023");
        assertEquals(A1 + "\n", f.read("ACCTFILE.txt"));
    }

    @Test
    void abendMissingXref() throws Exception {
        Fx f = new Fx(tmp);
        f.base(List.of(Fx.tcat("00000000003", "01", "0001", "0000012000{")), List.of(Fx.acct("00000000003", "00000000000{", "A")));
        assertAbend(f, "ACCOUNT NOT FOUND: 00000000003", "ERROR READING XREF FILE", "FILE STATUS IS: NNNN0023");
    }

    @Test
    void abendMissingDefaultRate() throws Exception {
        Fx f = new Fx(tmp);
        f.base(List.of(Fx.tcat("00000000001", "02", "0009", "0000012000{")), List.of(A1));
        assertAbend(f, "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023");
    }

    @Test
    void zeroRateNoTxnButRewrite() throws Exception {
        Fx f = new Fx(tmp);
        // a/c 1 has only a rate-0 category, a/c 2 follows: a/c 1 is rewritten (cycle fields zeroed), no txn, no counter use
        f.base(List.of(Fx.tcat("00000000001", "03", "0001", "0000012000{"),
                Fx.tcat("00000000002", "01", "0001", "0000012000{")), List.of(A1, A2));
        assertEquals(0, f.run("2022071800"));
        String[] txn = f.read("TRANSACT.txt").split("\n");
        assertEquals(1, txn.length);
        assertTrue(txn[0].startsWith("2022071800000001")); // counter not consumed by rate-0 record
        assertTrue(txn[0].contains("a/c 00000000002"));
        String[] acc = f.read("ACCTFILE.txt").split("\n");
        assertEquals("00000100000{", acc[0].substring(12, 24)); // balance unchanged
        assertEquals("00000000000{", acc[0].substring(78, 90));
        assertEquals("00000000000{", acc[0].substring(90, 102));
    }

    @Test
    void emptyTcatbal() throws Exception {
        Fx f = new Fx(tmp);
        f.base(List.of(), List.of(A1));
        assertEquals(0, f.run("2022071800"));
        assertEquals("", f.read("TRANSACT.txt"));
        assertEquals(A1 + "\n", f.read("ACCTFILE.txt"));
    }

    @Test
    void shortParmPaddedAndLongCut() throws Exception {
        Fx f = new Fx(tmp);
        f.base(List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{")), List.of(A1));
        assertEquals(0, f.run("AB"));
        assertTrue(f.read("TRANSACT.txt").startsWith("AB        000001"));
        assertEquals(0, f.run("0123456789XYZ"));
        assertTrue(f.read("TRANSACT.txt").startsWith("0123456789000001"));
    }
}
