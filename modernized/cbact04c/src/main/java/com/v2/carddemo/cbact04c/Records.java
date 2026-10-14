package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;

/** Fixed-width layouts (see app/cpy/CVTRA01Y, CVACT03Y, CVACT01Y, CVTRA02Y, CVTRA05Y). */
final class Records {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int DISCGRP_LEN = 50;
    static final int TRAN_LEN = 350;

    private Records() {}

    /** Pad with spaces / cut to {@code width}. */
    static String fit(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    /** TRAN-RECORD as written by 1300-B-WRITE-TX; unset bytes are spaces. */
    static String tranRecord(String id, String acctId, BigDecimal amount, String cardNum, String ts) {
        return fit(id, 16)
                + "01"
                + "0005"
                + fit("System", 10)
                + fit("Int. for a/c " + acctId, 100)
                + ZonedDecimal.encode(amount, 2, 11)
                + "000000000"
                + " ".repeat(50 + 50 + 10)
                + fit(cardNum, 16)
                + ts
                + ts
                + " ".repeat(20);
    }
}
