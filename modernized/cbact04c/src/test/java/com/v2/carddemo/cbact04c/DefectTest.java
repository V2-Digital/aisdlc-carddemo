package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy defects (intent D1-D7). Do not "fix" these in CR-1. */
class DefectTest {
    private Result afterCbtrn(Path out) throws IOException {
        Path prev = GOLDEN.resolve("CBTRN02C");
        return run(prev.resolve("TCATBALF.txt"), ASCII.resolve("cardxref.txt"), ASCII.resolve("discgrp.txt"),
                prev.resolve("ACCTFILE.txt"), out);
    }

    /** D1 */
    @Test
    void lastAccountNotRewritten(@TempDir Path out) throws IOException {
        Result r = afterCbtrn(out);
        String[] before = text(GOLDEN.resolve("CBTRN02C/ACCTFILE.txt")).split("\n");
        String[] after = text(out.resolve("ACCTFILE.txt")).split("\n");
        assertEquals(50, after.length);
        int changed = 0;
        for (int i = 0; i < 50; i++) {
            if (!before[i].equals(after[i])) changed++;
        }
        assertEquals(49, changed);
        assertTrue(after[49].startsWith("00000000050"));
        assertEquals(before[49], after[49], "last account (interest 18.77 charged) is left untouched");
        // ...yet its interest transaction was written
        String last = text(out.resolve("TRANSACT.txt")).split("\n")[49];
        assertTrue(last.startsWith("2022071800000050"));
        assertTrue(last.contains("Int. for a/c 00000000050"));
        assertEquals("0000000187" + "G", last.substring(132, 143).substring(0, 10) + "G"); // 18.77 -> ...187G
    }

    /** D2 */
    @Test
    void feesStepIsNoOp(@TempDir Path out) throws IOException {
        afterCbtrn(out);
        // Only interest transactions (type 01, cat 0005) exist; no fee transaction is ever produced.
        for (String t : text(out.resolve("TRANSACT.txt")).split("\n")) {
            assertEquals("010005", t.substring(16, 22));
        }
        assertEquals(50, text(out.resolve("TRANSACT.txt")).lines().count());
    }

    /** D3 */
    @Test
    void fallsBackToDefaultGroup(@TempDir Path out) throws IOException {
        Result r = afterCbtrn(out);
        long missing = r.stdout().lines().filter("DISCLOSURE GROUP RECORD MISSING"::equals).count();
        long retry = r.stdout().lines().filter("TRY WITH DEFAULT GROUP CODE"::equals).count();
        assertEquals(100, missing);
        assertEquals(100, retry);
    }

    /** D3 inverse: a group that does exist is used, no fallback message. */
    @Test
    void existingGroupIsUsedWithoutFallback(@TempDir Path d) throws IOException {
        Path tcat = write(d, "t", tcat("00000000001", "01", "0001", "0000001200{"));
        Path acct = write(d, "a", acct("00000000001", "00000000000{", "GRP"));
        Path xref = write(d, "x", xref("4111111111111111", "00000000001"));
        Path disc = write(d, "d", disc("GRP       010001", "00120{"), disc("DEFAULT   010001", "00010{"));
        Result r = run(tcat, xref, disc, acct, d.resolve("o"));
        assertEquals(0, r.rc());
        assertFalse(r.stdout().contains("MISSING"));
        // 120.00 * 12.00 / 1200 = 1.20 (DEFAULT 1.00% would give 0.10)
        assertEquals("0000000012{", text(d.resolve("o/TRANSACT.txt")).substring(132, 143));
    }

    /** D7: rows of an account are rated with the account record read at the control break. */
    @Test
    void allRowsOfAccountUseHeldAccountRecordAndCard(@TempDir Path d) throws IOException {
        Path tcat = write(d, "t", tcat("00000000001", "01", "0001", "0000010000{"),
                tcat("00000000001", "02", "0001", "0000010000{"));
        Path acct = write(d, "a", acct("00000000001", "00000000000{", ""));
        Path xref = write(d, "x", xref("4111111111111111", "00000000001"));
        Path disc = write(d, "d", disc("DEFAULT   010001", "00120{"), disc("DEFAULT   020001", "00120{"));
        Result r = run(tcat, xref, disc, acct, d.resolve("o"));
        assertEquals(0, r.rc());
        String[] tx = text(d.resolve("o/TRANSACT.txt")).split("\n");
        assertEquals(2, tx.length);
        for (String t : tx) {
            assertTrue(t.contains("Int. for a/c 00000000001"));
            assertEquals("4111111111111111", t.substring(262, 278));
        }
        // single account => it is the last one => D1: master not rewritten
        assertEquals(text(d.resolve("a")), text(d.resolve("o/ACCTFILE.txt")));
    }

    /** D5 */
    @Test
    void discgrpOpenErrorSaysDalyRejects(@TempDir Path d) throws IOException {
        Result r = run(ASCII.resolve("tcatbal.txt"), ASCII.resolve("cardxref.txt"), d.resolve("missing"),
                ASCII.resolve("acctdata.txt"), d.resolve("o"));
        assertEquals(16, r.rc());
        assertTrue(r.stdout().contains("ERROR OPENING DALY REJECTS FILE\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n"));
    }

    /** Rate 0 => no transaction, suffix does not advance; account still zeroed at next break. */
    @Test
    void zeroRateWritesNothingAndSuffixDoesNotAdvance(@TempDir Path d) throws IOException {
        Path tcat = write(d, "t",
                tcat("00000000001", "03", "0001", "0000010000{"),
                tcat("00000000002", "01", "0001", "0000010000{"),
                tcat("00000000003", "01", "0001", "0000010000{"));
        Path acct = write(d, "a", acct("00000000001", "00000000100{", ""), acct("00000000002", "00000000200{", ""),
                acct("00000000003", "00000000300{", ""));
        Path xref = write(d, "x", xref("1111111111111111", "00000000001"), xref("2222222222222222", "00000000002"),
                xref("3333333333333333", "00000000003"));
        Path disc = write(d, "d", disc("DEFAULT   030001", "00000{"), disc("DEFAULT   010001", "00120{"));
        Result r = run(tcat, xref, disc, acct, d.resolve("o"));
        assertEquals(0, r.rc());
        String[] tx = text(d.resolve("o/TRANSACT.txt")).split("\n");
        assertEquals(2, tx.length);
        assertTrue(tx[0].startsWith("2022071800000001"));
        assertTrue(tx[1].startsWith("2022071800000002"));
        String[] ac = text(d.resolve("o/ACCTFILE.txt")).split("\n");
        assertEquals("00000000001", ac[0].substring(0, 11));
        assertEquals(Support.acct("00000000001", "00000000100{", ""), ac[0]);
        assertEquals(Zoned.encode(2000 + 1000, 12), ac[1].substring(12, 24)); // 20.00 + 10.00 interest (1000.00 @ 12%)
    }
}
