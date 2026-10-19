package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @TempDir
    Path tmp;

    /** Runs two accounts (so the first is processed) and returns the TRANSACT amount field of the first. */
    private String amountFor(String bal, String rate) throws Exception {
        Fx f = new Fx(tmp)
                .file("TCATBALF", List.of(Fx.tcat(1, "01", "0001", bal)))
                .file("XREFFILE", List.of(Fx.xref("1111222233334444", 1)))
                .file("ACCTFILE", List.of(Fx.acct(1, Fx.Z12, Fx.Z12, Fx.Z12, "")))
                .file("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", rate)))
                .run("2022071800");
        assertEquals(0, f.rc);
        return Records.field(f.outRecs("TRANSACT", 350).get(0), 133, 11);
    }

    @Test
    void account2TruncatesTo29_24() throws Exception {
        // 2339.97 * 15.00 / 1200 = 29.2496 -> 29.24 (no ROUNDED)
        assertEquals("0000000292D", amountFor("00000233997", "00150{"));
    }

    @Test
    void negativeInterestBelowOneCentTruncatesToZero() throws Exception {
        // -0.50 * 1.00 / 1200 = -0.0004 -> 0.00, written as positive zero
        assertEquals("0000000000{", amountFor("0000000005}", "00010{"));
    }

    @Test
    void negativeInterestKeepsSign() throws Exception {
        // -1200.00 * 3.00 / 1200 = -3.00
        assertEquals("0000000030}", amountFor("0000012000}", "00030{"));
    }

    @Test
    void monthlyOverflowDropsHighOrderDigits() throws Exception {
        // 999999999.99 * 9999.99 / 1200 ~ 8.3e9 exceeds S9(9)V99
        BigDecimal exact = new BigDecimal("999999999.99").multiply(new BigDecimal("9999.99"))
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN);
        String got = amountFor("9999999999I", "99999I");
        assertEquals(Zoned.format(Zoned.trunc(exact, 9), 9), got);
        assertEquals(true, exact.compareTo(new BigDecimal("999999999.99")) > 0, "test must actually overflow");
    }
}
