package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Like-for-like port of CBACT04C (interest calculation). Legacy defects are preserved on purpose:
 * the last account is never rewritten, interest is truncated, and so on.
 */
public final class Cbact04c {

    /** Outputs of a run. {@code transact} and {@code accounts} hold what was written so far on an abend. */
    public record Result(int rc, List<String> transact, TreeMap<String, String> accounts) {
    }

    /** CEE3ABD stand-in. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    static final int ABEND_RC = 16;

    private final PrintStream sysout;
    private final Supplier<String> clock;
    private final String parmDate;
    private final TreeMap<String, String> tcatbal;
    private final Map<String, String> xrefByAcct = new HashMap<>();
    private final TreeMap<String, String> disc;
    private final TreeMap<String, String> accounts;
    private final List<String> transact = new ArrayList<>();
    private int tranIdSuffix;

    /**
     * @param clock supplies the 16-digit {@code YYYYMMDDHHMMSSss} value of FUNCTION CURRENT-DATE,
     *              called once per transaction
     * @param xref  XREFFILE keyed by card number; any input map may be null (file missing)
     */
    public Cbact04c(String parm, Supplier<String> clock, PrintStream sysout,
                    TreeMap<String, String> tcatbal, TreeMap<String, String> xref,
                    TreeMap<String, String> disc, TreeMap<String, String> accounts) {
        this.parmDate = Records.pad(parm, 10).substring(0, 10);
        this.clock = clock;
        this.sysout = sysout;
        this.tcatbal = tcatbal;
        this.disc = disc;
        this.accounts = accounts;
        if (xref != null) {
            // AIX read returns the first card (card-number order) for an account
            for (String r : xref.values()) {
                xrefByAcct.putIfAbsent(Records.xrefAcctId(r), Records.xrefCard(r));
            }
        }
        this.xrefMissing = xref == null;
    }

    private final boolean xrefMissing;

    private void display(String s) {
        sysout.print(s + "\n");
    }

    private void abend(String status) {
        display("FILE STATUS IS: NNNN00" + status);
        display("ABENDING PROGRAM");
        throw new Abend();
    }

    public Result run() {
        try {
            execute();
            return new Result(0, transact, accounts);
        } catch (Abend a) {
            System.err.println("CEE3ABD: ABEND U0999");
            return new Result(ABEND_RC, transact, accounts);
        }
    }

    private void openOrAbend(boolean missing, String message) {
        if (missing) {
            display(message);
            abend("35");
        }
    }

    private void execute() {
        display("START OF EXECUTION OF PROGRAM CBACT04C");
        openOrAbend(tcatbal == null, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        openOrAbend(xrefMissing, "ERROR OPENING CROSS REF FILE35");
        openOrAbend(disc == null, "ERROR OPENING DALY REJECTS FILE");
        openOrAbend(accounts == null, "ERROR OPENING ACCOUNT MASTER FILE");

        String lastAcct = " ".repeat(11);
        boolean first = true;
        BigDecimal totalInt = BigDecimal.ZERO;
        String acct = null;
        String card = null;

        for (String rec : tcatbal.values()) {
            display(rec);
            String acctId = Records.tcatAcctId(rec);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    rewrite(acct, totalInt);
                } else {
                    first = false;
                }
                totalInt = BigDecimal.ZERO;
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING ACCOUNT FILE");
                    abend("23");
                }
                card = xrefByAcct.get(acctId);
                if (card == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING XREF FILE");
                    abend("23");
                }
            }
            String typeCd = Records.tcatTypeCd(rec);
            String cd = Records.tcatCd(rec);
            BigDecimal rate = rate(Records.acctGroupId(acct), typeCd, cd);
            if (rate.signum() != 0) {
                // 1300-COMPUTE-INTEREST: truncated, not rounded (D2); 1400-COMPUTE-FEES is empty (D4)
                BigDecimal monthly = Records.tcatBal(rec).multiply(rate)
                        .divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN);
                monthly = wrap(monthly);
                totalInt = wrap(totalInt.add(monthly));
                tranIdSuffix = (tranIdSuffix + 1) % 1_000_000;
                transact.add(Records.tran(parmDate, tranIdSuffix, Records.acctId(acct), monthly,
                        card, timestamp()));
            }
        }
        // D1: no rewrite of the last account at EOF
        display("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    /** Store into S9(9)V99: drop high-order digits. */
    private static BigDecimal wrap(BigDecimal v) {
        return Zoned.decode(Zoned.encode(v, 11, 2), 2);
    }

    private void rewrite(String acct, BigDecimal totalInt) {
        accounts.put(Records.acctId(acct), Records.acctUpdate(acct, totalInt));
    }

    private BigDecimal rate(String group, String typeCd, String cd) {
        String r = disc.get(Records.discKey(group, typeCd, cd));
        if (r == null) {
            display("DISCLOSURE GROUP RECORD MISSING");
            display("TRY WITH DEFAULT GROUP CODE");
            r = disc.get(Records.discKey("DEFAULT", typeCd, cd));
            if (r == null) {
                display("ERROR READING DEFAULT DISCLOSURE GROUP");
                abend("23");
            }
        }
        return Records.discRate(r);
    }

    /** Z-GET-DB2-FORMAT-TIMESTAMP: YYYY-MM-DD-HH.MM.SS.hh0000 */
    private String timestamp() {
        String t = clock.get();
        return t.substring(0, 4) + "-" + t.substring(4, 6) + "-" + t.substring(6, 8) + "-"
                + t.substring(8, 10) + "." + t.substring(10, 12) + "." + t.substring(12, 14) + "."
                + t.substring(14, 16) + "0000";
    }
}
