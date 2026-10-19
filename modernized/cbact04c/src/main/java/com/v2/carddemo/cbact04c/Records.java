package com.v2.carddemo.cbact04c;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Column slices (1-based columns in the comments) for the records CBACT04C touches. */
public final class Records {
    public static final int TCATBAL_LEN = 50;
    public static final int XREF_LEN = 50;
    public static final int DISC_LEN = 50;
    public static final int ACCT_LEN = 300;
    public static final int TRAN_LEN = 350;

    private Records() {}

    public static String tcatAcct(String r) { return r.substring(0, 11); }       // 1-11
    public static String tcatType(String r) { return r.substring(11, 13); }      // 12-13
    public static String tcatCat(String r) { return r.substring(13, 17); }       // 14-17
    public static long tcatBal(String r) { return Zoned.decode(r.substring(17, 28)); } // 18-28 S9(9)V99

    public static String xrefCard(String r) { return r.substring(0, 16); }       // 1-16
    public static String xrefAcct(String r) { return r.substring(25, 36); }      // 26-36

    public static String acctId(String r) { return r.substring(0, 11); }         // 1-11
    public static long acctBal(String r) { return Zoned.decode(r.substring(12, 24)); } // 13-24 S9(10)V99
    public static String acctGroup(String r) { return r.substring(112, 122); }    // 113-122

    public static String discKey(String r) { return r.substring(0, 16); }        // 1-16
    public static long discRate(String r) { return Zoned.decode(r.substring(16, 22)); } // 17-22 S9(4)V99

    /** Account after REWRITE: balance updated, cycle credit/debit zeroed; every other byte untouched. */
    public static String rewriteAccount(String r, long newBal) {
        String zero = Zoned.encode(0, 12);
        return r.substring(0, 12) + Zoned.encode(newBal, 12) + r.substring(24, 78) + zero + zero + r.substring(102);
    }

    /** The 350-byte interest transaction. Unset fields stay spaces (record is never initialised). */
    public static String tran(String parmDate, int counter, String acctId, long amt, String card, Clock clock) {
        String ts = timestamp(clock);
        StringBuilder sb = new StringBuilder(TRAN_LEN);
        sb.append(parmDate).append(String.format("%06d", counter))   // TRAN-ID 16
          .append("01").append("0005")                                // type, cat
          .append(pad("System", 10))                                  // source
          .append(pad("Int. for a/c " + acctId, 100))                 // desc
          .append(Zoned.encode(amt, 11))                              // amount
          .append("000000000")                                        // merchant id
          .append(" ".repeat(50 + 50 + 10))                           // merchant name, city, zip
          .append(card)                                               // card
          .append(ts).append(ts)                                      // orig/proc ts
          .append(" ".repeat(20));                                    // filler
        return sb.toString();
    }

    /** {@code YYYY-MM-DD-HH.MM.SS.hh0000} */
    static String timestamp(Clock clock) {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    public static String pad(String s, int len) {
        return s.length() >= len ? s.substring(0, len) : s + " ".repeat(len - s.length());
    }
}
