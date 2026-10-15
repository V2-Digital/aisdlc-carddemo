package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    static final int AMT = 10 + 6 + 2 + 4 + 10 + 100;

    /** One account at 15% plus a dummy second account, so the first is rewritten. Returns TRANSACT amount. */
    private String amountFor(Path tmp, String bal) throws IOException {
        Path in = tmp.resolve("in");
        put(in, "TCATBALF", tcat("00000000001", "01", "0001", bal), tcat("00000000002", "03", "0001", Z12));
        put(in, "XREFFILE", xref("1111222233334444", "00000000001"), xref("5555666677778888", "00000000002"));
        put(in, "ACCTFILE", acct("00000000001", Z12, "", Z12, Z12), acct("00000000002", Z12, "", Z12, Z12));
        put(in, "DISCGRP", disc("DEFAULT", "01", "0001", "00150{"),
                disc("DEFAULT", "03", "0001", "00000{"));
        Result r = run(in, tmp.resolve("out"));
        assertEquals(0, r.rc());
        return r.file("TRANSACT.txt").substring(AMT, AMT + 11);
    }

    @Test
    void truncatesNotRounds(@TempDir Path tmp) throws IOException {
        // 100.40 x 15 / 1200 = 1.255 -> 1.25 (rounding would give 1.26)
        assertEquals("0000000012E", amountFor(tmp, "0000001004{"));
    }

    @Test
    void negativeTruncatesTowardZero(@TempDir Path tmp) throws IOException {
        // -100.40 -> -1.25 (rounding would give -1.26)
        assertEquals("0000000012N", amountFor(tmp, "0000001004}"));
    }

    @Test
    void negativeSubCentTruncatesToPositiveZero(@TempDir Path tmp) throws IOException {
        // -0.04 x 15 / 1200 = -0.0005 -> 0.00, encoded '{'
        assertEquals("0000000000{", amountFor(tmp, "0000000000M"));
    }
}
