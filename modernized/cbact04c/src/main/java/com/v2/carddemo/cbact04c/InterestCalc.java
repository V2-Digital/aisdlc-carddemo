package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Port of CBACT04C (interest calculation). Legacy defects are preserved on purpose; see README. */
public final class InterestCalc {
    private static final BigDecimal MONTHS = new BigDecimal(1200);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.");

    public record Result(List<String> transactions, List<String> accounts) {}

    private final String parm;
    private final Clock clock;
    private final List<String> sysout;

    public InterestCalc(String parm, Clock clock, List<String> sysout) {
        this.parm = FixedText.pad(parm, 10);
        this.clock = clock;
        this.sysout = sysout;
    }

    /**
     * @param tcatbal TCATBAL records in key order
     * @param xref    XREF records in file order
     * @param accts   ACCTFILE records
     * @param discgrp DISCGRP records
     */
    public Result run(List<String> tcatbal, List<String> xref, List<String> accts, List<String> discgrp) {
        Map<String, String> acctMap = new TreeMap<>();
        for (String a : accts) acctMap.put(Records.acctId(a), a);
        Map<String, String> xrefByAcct = new java.util.HashMap<>();
        for (String x : xref) xrefByAcct.putIfAbsent(Records.xrefAcct(x), x);
        Map<String, String> rates = new java.util.HashMap<>();
        for (String d : discgrp) rates.put(d.substring(0, 16), d);

        List<String> trans = new ArrayList<>();
        sysout.add("START OF EXECUTION OF PROGRAM CBACT04C");

        String lastAcct = "";
        boolean first = true;
        String acct = null;
        String card = null;
        BigDecimal total = BigDecimal.ZERO;
        int suffix = 0;

        for (String rec : tcatbal) {
            sysout.add(rec);
            String acctId = Records.tcatAcct(rec);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    // 1050-UPDATE-ACCOUNT; never runs for the last account (F1)
                    acctMap.put(Records.acctId(acct),
                        Records.acctUpdate(acct, Zoned.truncate(Records.acctBal(acct).add(total), 10)));
                } else {
                    first = false;
                }
                total = BigDecimal.ZERO;
                lastAcct = acctId;
                acct = acctMap.get(acctId);
                if (acct == null) throw missing(acctId, "ERROR READING ACCOUNT FILE");
                card = xrefByAcct.get(acctId);
                if (card == null) throw missing(acctId, "ERROR READING XREF FILE");
            }

            String type = Records.tcatType(rec), cat = Records.tcatCat(rec);
            String d = rates.get(Records.discKey(Records.acctGroup(acct), type, cat));
            if (d == null) {
                sysout.add("DISCLOSURE GROUP RECORD MISSING");
                sysout.add("TRY WITH DEFAULT GROUP CODE");
                d = rates.get(Records.discKey("DEFAULT   ", type, cat));
                if (d == null) {
                    throw new Abend("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                        "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023");
                }
            }
            BigDecimal rate = Records.discRate(d);
            if (rate.signum() != 0) {
                BigDecimal monthly = Zoned.truncate(
                    Records.tcatBal(rec).multiply(rate).divide(MONTHS, 2, RoundingMode.DOWN), 9);
                total = Zoned.truncate(total.add(monthly), 9);
                suffix = (suffix + 1) % 1_000_000;
                trans.add(Records.tran(parm + String.format("%06d", suffix), Records.acctId(acct), monthly,
                    Records.xrefCard(card), timestamp()));
                // 1400-COMPUTE-FEES is an empty stub (F3)
            }
        }

        sysout.add("END OF EXECUTION OF PROGRAM CBACT04C");
        return new Result(trans, new ArrayList<>(acctMap.values()));
    }

    private Abend missing(String acctId, String msg) {
        return new Abend("ACCOUNT NOT FOUND: " + acctId, msg, "FILE STATUS IS: NNNN0023");
    }

    /** DB2 style timestamp from the clock: hundredths followed by "0000" (F4). */
    private String timestamp() {
        ZonedDateTime t = ZonedDateTime.now(clock);
        return t.format(TS) + String.format("%02d", t.getNano() / 10_000_000) + "0000";
    }
}
