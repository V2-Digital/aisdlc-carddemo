package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;

/** Column layouts from the copybooks (0-based substring offsets) and record builders. */
public final class Records {
    private Records() {}

    public static final int TCATBAL_LEN = 50, XREF_LEN = 50, ACCT_LEN = 300, DISCGRP_LEN = 50, TRAN_LEN = 350;

    // CVTRA01Y: acct 1-11, type 12-13, cat 14-17, balance 18-28
    public static String tcatAcct(String r) { return r.substring(0, 11); }
    public static String tcatType(String r) { return r.substring(11, 13); }
    public static String tcatCat(String r) { return r.substring(13, 17); }
    public static BigDecimal tcatBal(String r) { return Zoned.decode(r.substring(17, 28)); }

    // CVACT03Y: card 1-16, acct 26-36
    public static String xrefCard(String r) { return r.substring(0, 16); }
    public static String xrefAcct(String r) { return r.substring(25, 36); }

    // CVACT01Y: id 1-11, curr bal 13-24, cycle credit 79-90, cycle debit 91-102, group 113-122
    public static String acctId(String r) { return r.substring(0, 11); }
    public static BigDecimal acctBal(String r) { return Zoned.decode(r.substring(12, 24)); }
    public static String acctGroup(String r) { return r.substring(112, 122); }

    /** Replaces CURR-BAL and zeroes the cycle credit/debit; every other byte is kept. */
    public static String acctUpdate(String r, BigDecimal newBal) {
        String zero = Zoned.encode(BigDecimal.ZERO, 10);
        return r.substring(0, 12) + Zoned.encode(newBal, 10) + r.substring(24, 78) + zero + zero + r.substring(102);
    }

    // CVTRA02Y: group 1-10, type 11-12, cat 13-16, rate 17-22
    public static String discKey(String group, String type, String cat) { return group + type + cat; }
    public static BigDecimal discRate(String r) { return Zoned.decode(r.substring(16, 22)); }

    /** CVTRA05Y interest transaction. */
    public static String tran(String id, String acctId, BigDecimal amt, String card, String ts) {
        return FixedText.pad(id, 16) + "01" + "0005" + FixedText.pad("System", 10)
            + FixedText.pad("Int. for a/c " + acctId, 100)
            + Zoned.encode(amt, 9) + "000000000"
            + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
            + FixedText.pad(card, 16) + ts + ts + " ".repeat(20);
    }
}
