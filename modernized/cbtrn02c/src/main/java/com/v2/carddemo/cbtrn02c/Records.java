package com.v2.carddemo.cbtrn02c;

/** 1-based field accessors over fixed-width record strings. Untouched bytes survive a round trip. */
final class Records {
    private Records() {}

    static String get(String rec, int off, int len) {
        return rec.substring(off - 1, off - 1 + len);
    }

    static String put(String rec, int off, String val) {
        return rec.substring(0, off - 1) + val + rec.substring(off - 1 + val.length());
    }

    static long money(String rec, int off, int len) {
        return Zoned.decode(get(rec, off, len));
    }

    // DALYTRAN / TRANFILE (CVTRA06Y / CVTRA05Y)
    static final int TRAN_LEN = 350;
    static final int T_ID = 1, T_TYPE = 17, T_CAT = 19, T_AMT = 133, T_CARD = 263, T_ORIG_TS = 279, T_PROC_TS = 305,
            T_FILLER = 331;

    // XREFFILE (CVACT03Y)
    static final int XREF_LEN = 50, X_ACCT = 26;

    // ACCTFILE (CVACT01Y)
    static final int ACCT_LEN = 300, A_BAL = 13, A_LIMIT = 25, A_EXPIRY = 59, A_CYC_CREDIT = 79, A_CYC_DEBIT = 91;

    // TCATBALF (CVTRA01Y)
    static final int TCAT_LEN = 50, C_BAL = 18, C_FILLER = 29;

    static final int REJ_LEN = 430;
}
