package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;

/** Column accessors for the records CBACT04C touches (offsets are 0-based substring bounds). */
final class Records {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int DISCGRP_LEN = 50;
    static final int TRAN_LEN = 350;

    private Records() {}

    // TCATBAL (CVTRA01Y)
    static String tcatKey(String r) { return r.substring(0, 17); }
    static String tcatAcct(String r) { return r.substring(0, 11); }
    static String tcatType(String r) { return r.substring(11, 13); }
    static String tcatCat(String r) { return r.substring(13, 17); }
    static BigDecimal tcatBal(String r) { return Zoned.parse(r.substring(17, 28), 2); }

    // XREF (CVACT03Y)
    static String xrefCard(String r) { return r.substring(0, 16); }
    static String xrefAcct(String r) { return r.substring(25, 36); }

    // DISCGRP (CVTRA02Y)
    static String discKey(String r) { return r.substring(0, 16); }
    static BigDecimal discRate(String r) { return Zoned.parse(r.substring(16, 22), 2); }

    // ACCT (CVACT01Y)
    static String acctId(String r) { return r.substring(0, 11); }
    static String acctGroup(String r) { return r.substring(112, 122); }
    static BigDecimal acctBal(String r) { return Zoned.parse(r.substring(12, 24), 2); }

    /** Applies 1050-UPDATE-ACCOUNT: balance += total, cycle credit/debit = 0; other bytes verbatim. */
    static String acctUpdated(String r, BigDecimal total) {
        BigDecimal bal = Zoned.truncate(acctBal(r).add(total), 10, 2);
        String zero = Zoned.format(BigDecimal.ZERO.setScale(2), 12);
        return r.substring(0, 12) + Zoned.format(bal, 12) + r.substring(24, 78) + zero + zero + r.substring(102);
    }

    /** Builds a TRAN-RECORD (CVTRA05Y) as written by 1300-B-WRITE-TX. */
    static String tran(String id, String acctId, BigDecimal amt, String card, String ts) {
        return id + "01" + "0005" + RecordFile.pad("System", 10)
                + RecordFile.pad("Int. for a/c " + acctId, 100)
                + Zoned.format(amt, 11) + "000000000"
                + " ".repeat(50 + 50 + 10)
                + card + ts + ts + " ".repeat(20);
    }
}
