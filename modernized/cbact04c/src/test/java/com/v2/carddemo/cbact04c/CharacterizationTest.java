package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins legacy behaviour, defects included (F1, F3, F6, rate 0, timestamp, ids, bad input). */
class CharacterizationTest {
    private static Harness.Result afterPosting() throws IOException {
        Path in = Files.createTempDirectory("in");
        for (String dd : new String[] {"ACCTFILE", "TCATBALF"}) {
            Files.copy(Harness.GOLDEN.resolve("CBTRN02C/" + dd + ".txt"), in.resolve(dd + ".txt"));
        }
        return Harness.run(in, "2022071800000000");
    }

    private static String line(String text, int n) {
        return text.split("\n")[n - 1];
    }

    /** Stock XREF/DISCGRP/ACCTFILE with the given TCATBAL lines (acct, type+cat, zoned balance). */
    private static InterestCalc build(List<String> tcat) throws IOException {
        return new InterestCalc("2022071800", () -> "2022071800000000", s -> { }, tcat,
                Records.read(Harness.STOCK.resolve("cardxref.txt"), 50, "XREFFILE"),
                Records.read(Harness.STOCK.resolve("discgrp.txt"), 50, "DISCGRP"),
                Records.read(Harness.STOCK.resolve("acctdata.txt"), 300, "ACCTFILE"));
    }

    private static String tcat(String acct, String balance) {
        return Records.pad(acct + "010001" + balance, 50);
    }

    @Test
    void f1LastAccountNotRewritten() throws IOException {
        Harness.Result r = afterPosting();
        String posted = Files.readString(Harness.GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"));
        assertTrue(r.transact().contains("Int. for a/c 00000000050"), "interest is charged");
        assertEquals(line(posted, 50), line(r.acctfile(), 50), "but account 50 is unchanged");
        assertNotEquals(line(posted, 49), line(r.acctfile(), 49));
    }

    @Test
    void rateZeroWritesNoTransactionButCycleFieldsReset() throws IOException {
        Harness.Result r = afterPosting();
        // 2 TCATBAL records per account (01/0001 and 03/0001 with rate 0): one transaction per account
        assertEquals(50, r.transact().split("\n").length);
        assertEquals("00000000000{00000000000{", line(r.acctfile(), 1).substring(78, 102));
    }

    @Test
    void interestIsTruncatedNotRounded() throws IOException {
        // 7.77 * 15 / 1200 = 0.097125: truncated 0.09, rounded would be 0.10
        InterestCalc c = build(List.of(tcat("00000000001", "0000000077G"), tcat("00000000002", "0000000000{")));
        c.run();
        assertEquals("0000000000I", c.transactions().get(0).substring(132, 143));
    }

    @Test
    void timestampAndIds() throws IOException {
        InterestCalc c = build(List.of(tcat("00000000001", "0000010000{")));
        c.run();
        String t = c.transactions().get(0);
        assertEquals("2022071800000001", t.substring(0, 16));
        assertEquals("2022-07-18-00.00.00.000000", t.substring(278, 304));
        assertEquals(t.substring(278, 304), t.substring(304, 330));
        assertEquals("010005System    Int. for a/c 00000000001", t.substring(16, 56));
        assertEquals(350, t.length());
    }

    @Test
    void tranIdSuffixWrapsAfter999999() throws IOException {
        List<String> many = new ArrayList<>();
        String rec = tcat("00000000001", "0000000100{");
        for (int i = 0; i < 1_000_001; i++) {
            many.add(rec);
        }
        InterestCalc c = build(many);
        c.run();
        assertEquals("2022071800999999", c.transactions().get(999_998).substring(0, 16));
        assertEquals("2022071800000000", c.transactions().get(999_999).substring(0, 16));
        assertEquals("2022071800000001", c.transactions().get(1_000_000).substring(0, 16));
    }

    @Test
    void badInputAbends() throws IOException {
        InterestCalc c = build(List.of(tcat("00000000001", "00000ZZ0000")));
        assertThrows(Abend.class, c::run);
    }

    @Test
    void discgrpOpenFailureKeepsLegacyTypoText() {
        List<String> out = new ArrayList<>();
        assertThrows(Abend.class, () -> new InterestCalc("2022071800", () -> "", out::add, List.of(), List.of(),
                null, List.of()));
        assertTrue(out.contains("ERROR OPENING DALY REJECTS FILE"));
        assertTrue(out.contains("FILE STATUS IS: NNNN0035"));
    }
}
