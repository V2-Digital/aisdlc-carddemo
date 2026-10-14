package carddemo.cbact04c;

import java.math.BigDecimal;

/** Field layouts from CVTRA01Y, CVACT03Y, CVACT01Y, CVTRA02Y and CVTRA05Y (0-based offsets). */
final class Records {
    private Records() {}

    static final int TCATBAL_LEN = 50, XREF_LEN = 50, ACCT_LEN = 300, DISC_LEN = 50, TRAN_LEN = 350;

    // TRAN-CAT-BAL-RECORD
    static String tcatAcctId(String r) { return r.substring(0, 11); }
    static String tcatTypeCd(String r) { return r.substring(11, 13); }
    static String tcatCd(String r) { return r.substring(13, 17); }
    static BigDecimal tcatBal(String r) { return Zoned.decode(r.substring(17, 28), 2); }

    // CARD-XREF-RECORD
    static String xrefCardNum(String r) { return r.substring(0, 16); }
    static String xrefAcctId(String r) { return r.substring(25, 36); }

    // ACCOUNT-RECORD
    static String acctId(String r) { return r.substring(0, 11); }
    static BigDecimal acctCurrBal(String r) { return Zoned.decode(r.substring(12, 24), 2); }
    static String acctGroupId(String r) { return r.substring(112, 122); }

    /** ADD total TO ACCT-CURR-BAL; zero the cycle credit/debit; every other byte is kept. */
    static String acctUpdate(String r, BigDecimal totalInt) {
        BigDecimal bal = acctCurrBal(r).add(totalInt);
        String zero = Zoned.encode(BigDecimal.ZERO, 2, 12);
        return r.substring(0, 12) + Zoned.encode(bal, 2, 12) + r.substring(24, 78) + zero + zero + r.substring(102);
    }

    // DIS-GROUP-RECORD: key = group(10) + type(2) + cat(4)
    static String discKey(String r) { return r.substring(0, 16); }
    static BigDecimal discRate(String r) { return Zoned.decode(r.substring(16, 22), 2); }

    static String tran(String parm, int suffix, String acctId, BigDecimal amt, String cardNum, String ts) {
        return parm
                + String.format("%06d", suffix)
                + "01" + "0005" + "System    "
                + Datasets.pad("Int. for a/c " + acctId, 100)
                + Zoned.encode(amt, 2, 11)
                + "000000000"
                + " ".repeat(50 + 50 + 10)
                + cardNum
                + ts + ts
                + " ".repeat(20);
    }
}
