package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;

/** Field offsets (0-based substring bounds) for the layouts this program touches. */
final class Records {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int DISC_LEN = 50;
    static final int TRAN_LEN = 350;

    private Records() {
    }

    static String pad(String s, int len) {
        return s.length() >= len ? s : s + " ".repeat(len - s.length());
    }

    // TCATBALF (CVTRA01Y): key 1-17, TRAN-CAT-BAL 18-28
    static String tcatKey(String r) { return r.substring(0, 17); }
    static String tcatAcctId(String r) { return r.substring(0, 11); }
    static String tcatTypeCd(String r) { return r.substring(11, 13); }
    static String tcatCd(String r) { return r.substring(13, 17); }
    static BigDecimal tcatBal(String r) { return Zoned.decode(r.substring(17, 28), 2); }

    // XREFFILE (CVACT03Y): card 1-16, acct-id 26-36
    static String xrefCard(String r) { return r.substring(0, 16); }
    static String xrefAcctId(String r) { return r.substring(25, 36); }

    // ACCTFILE (CVACT01Y)
    static String acctId(String r) { return r.substring(0, 11); }
    static BigDecimal acctCurrBal(String r) { return Zoned.decode(r.substring(12, 24), 2); }
    static String acctGroupId(String r) { return r.substring(112, 122); }

    /** ADD total TO CURR-BAL, zero both cycle fields; other bytes pass through. */
    static String acctUpdate(String r, BigDecimal total) {
        String bal = Zoned.encode(acctCurrBal(r).add(total), 12, 2);
        String zero = Zoned.encode(BigDecimal.ZERO, 12, 2);
        return r.substring(0, 12) + bal + r.substring(24, 78) + zero + zero + r.substring(102);
    }

    // DISCGRP (CVTRA02Y): key 1-16, DIS-INT-RATE 17-22
    static String discKey(String group, String typeCd, String cd) {
        return pad(group, 10) + typeCd + cd;
    }
    static BigDecimal discRate(String r) { return Zoned.decode(r.substring(16, 22), 2); }

    /** TRAN-RECORD (CVTRA05Y) as written by 1300-B-WRITE-TX. */
    static String tran(String parmDate, int suffix, String acctId, BigDecimal amount,
                       String cardNum, String ts) {
        StringBuilder sb = new StringBuilder(TRAN_LEN);
        sb.append(parmDate).append(Zoned.unsigned(suffix, 6));
        sb.append("01").append("0005");
        sb.append(pad("System", 10));
        sb.append(pad("Int. for a/c " + acctId, 100));
        sb.append(Zoned.encode(amount, 11, 2));
        sb.append("000000000");
        sb.append(" ".repeat(110));
        sb.append(cardNum).append(ts).append(ts);
        sb.append(" ".repeat(20));
        return sb.toString();
    }
}
