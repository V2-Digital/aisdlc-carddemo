package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Like-for-like rewrite of CBACT04C (interest calculation). Legacy defects are preserved on purpose.
 * Records are fixed-width strings as in the harness text files (no line terminator).
 */
public final class Cbact04c {
    public static final int RC_OK = 0;
    public static final int RC_ABEND = 16;

    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    /** Input datasets; a {@code null} list means the file could not be opened. */
    public record Inputs(List<String> tcatbalf, List<String> xreffile, List<String> acctfile, List<String> discgrp) {}

    /** {@code transact} and {@code acctfile} are empty after an abend (nothing is exported). */
    public record Result(int rc, List<String> sysout, List<String> transact, List<String> acctfile) {}

    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    private final String parmDate;
    private final Supplier<String> clock;
    private final List<String> sysout = new ArrayList<>();
    private final List<String> transact = new ArrayList<>();
    private int tranIdSuffix;

    private Cbact04c(String parm, Supplier<String> clock) {
        this.parmDate = Records.fit(parm, 10);
        this.clock = clock;
    }

    /** @param clock yields {@code YYYYMMDDHHMMSSss} (16 digits) on each call, like FUNCTION CURRENT-DATE */
    public static Result run(String parm, Inputs in, Supplier<String> clock) {
        return new Cbact04c(parm, clock).execute(in);
    }

    private Result execute(Inputs in) {
        Map<String, String> accounts = new TreeMap<>();
        try {
            display("START OF EXECUTION OF PROGRAM CBACT04C");
            // Open order and legacy texts: 0000 .. 0400
            if (in.tcatbalf() == null) openError("ERROR OPENING TRANSACTION CATEGORY BALANCE", "");
            if (in.xreffile() == null) openError("ERROR OPENING CROSS REF FILE", "35");
            if (in.discgrp() == null) openError("ERROR OPENING DALY REJECTS FILE", ""); // D7
            if (in.acctfile() == null) openError("ERROR OPENING ACCOUNT MASTER FILE", "");

            List<String> tcatbal = new ArrayList<>();
            for (String r : in.tcatbalf()) tcatbal.add(Records.fit(r, Records.TCATBAL_LEN));
            tcatbal.sort(null); // KSDS key order: acct-id + type + cat (cols 0-16)
            // XREF alternate key: first card in card-number order wins.
            Map<String, String> xrefByAcct = new TreeMap<>();
            Map<String, String> xrefByCard = new TreeMap<>();
            for (String r : in.xreffile()) {
                String x = Records.fit(r, Records.XREF_LEN);
                xrefByCard.put(x.substring(0, 16), x);
            }
            for (String x : xrefByCard.values()) xrefByAcct.putIfAbsent(x.substring(25, 36), x);
            Map<String, String> discgrp = new TreeMap<>();
            for (String r : in.discgrp()) {
                String d = Records.fit(r, Records.DISCGRP_LEN);
                discgrp.put(d.substring(0, 16), d);
            }
            for (String r : in.acctfile()) {
                String a = Records.fit(r, Records.ACCT_LEN);
                accounts.put(a.substring(0, 11), a);
            }

            String lastAcct = "";
            boolean first = true;
            String acct = null;
            String xref = null;
            BigDecimal totalInt = BigDecimal.ZERO;
            String rate = null;
            for (String rec : tcatbal) {
                display(rec);
                String acctId = rec.substring(0, 11);
                if (!acctId.equals(lastAcct)) {
                    if (!first) {
                        accounts.put(acctId(acct), updateAccount(acct, totalInt));
                    }
                    first = false;
                    totalInt = BigDecimal.ZERO;
                    lastAcct = acctId;
                    acct = accounts.get(acctId);
                    if (acct == null) lookupFailed(acctId, "ERROR READING ACCOUNT FILE");
                    xref = xrefByAcct.get(acctId);
                    if (xref == null) lookupFailed(acctId, "ERROR READING XREF FILE");
                }
                String keyTail = rec.substring(11, 13) + rec.substring(13, 17);
                String key = Records.fit(acct.substring(112, 122), 10) + keyTail;
                String dg = discgrp.get(key);
                if (dg == null) {
                    display("DISCLOSURE GROUP RECORD MISSING");
                    display("TRY WITH DEFAULT GROUP CODE");
                    dg = discgrp.get(Records.fit("DEFAULT", 10) + keyTail);
                    if (dg == null) lookupFailed(null, "ERROR READING DEFAULT DISCLOSURE GROUP");
                }
                BigDecimal intRate = ZonedDecimal.decode(dg.substring(16, 22), 2);
                if (intRate.signum() != 0) {
                    BigDecimal bal = ZonedDecimal.decode(rec.substring(17, 28), 2);
                    BigDecimal monthly = interest(bal, intRate);
                    totalInt = ZonedDecimal.truncate(totalInt.add(monthly), 9);
                    writeTransaction(acctId, xref, monthly);
                    // 1400-COMPUTE-FEES is empty in the legacy program (CR-2)
                }
            }
            // D1: no update for the last account (the legacy ELSE branch is unreachable).
            display("END OF EXECUTION OF PROGRAM CBACT04C");
            return new Result(RC_OK, sysout, transact, new ArrayList<>(accounts.values()));
        } catch (Abend e) {
            return new Result(RC_ABEND, sysout, List.of(), List.of());
        }
    }

    /** {@code (bal * rate) / 1200} truncated toward zero to 2 dp, high-order digits cut to S9(9)V99. */
    public static BigDecimal interest(BigDecimal bal, BigDecimal rate) {
        BigDecimal q = bal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN);
        return ZonedDecimal.truncate(q, 9);
    }

    private static String acctId(String acct) {
        return acct.substring(0, 11);
    }

    private static String updateAccount(String acct, BigDecimal totalInt) {
        BigDecimal bal = ZonedDecimal.decode(acct.substring(12, 24), 2).add(totalInt);
        String zero = ZonedDecimal.encode(BigDecimal.ZERO, 2, 12);
        return acct.substring(0, 12) + ZonedDecimal.encode(bal, 2, 12) + acct.substring(24, 78)
                + zero + zero + acct.substring(102);
    }

    private void writeTransaction(String acctId, String xref, BigDecimal amount) {
        tranIdSuffix = (tranIdSuffix + 1) % 1_000_000;
        String id = parmDate + String.format("%06d", tranIdSuffix);
        transact.add(Records.tranRecord(id, acctId, amount, xref.substring(0, 16), timestamp(clock.get())));
    }

    /** {@code YYYY-MM-DD-HH.MM.SS.hh0000} from a 16-digit CURRENT-DATE value. */
    static String timestamp(String cd) {
        return cd.substring(0, 4) + "-" + cd.substring(4, 6) + "-" + cd.substring(6, 8) + "-"
                + cd.substring(8, 10) + "." + cd.substring(10, 12) + "." + cd.substring(12, 14) + "."
                + cd.substring(14, 16) + "0000";
    }

    private void display(String line) {
        sysout.add(line);
    }

    private void openError(String text, String statusSuffix) {
        display(text + statusSuffix);
        display("FILE STATUS IS: NNNN0035");
        abend();
    }

    /** INVALID KEY path: status 23. {@code acctId == null} for the DEFAULT disclosure group. */
    private void lookupFailed(String acctId, String text) {
        if (acctId != null) display("ACCOUNT NOT FOUND: " + acctId);
        display(text);
        display("FILE STATUS IS: NNNN0023");
        abend();
    }

    private void abend() {
        display("ABENDING PROGRAM");
        throw new Abend();
    }
}
