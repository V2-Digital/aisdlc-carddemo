package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Like-for-like port of CBACT04C. Legacy defects are preserved on purpose
 * (last account never rewritten, truncation, empty fees, silent overflow).
 */
final class InterestCalc {

    /** Input datasets, keyed as the KSDS would be; null means the file is missing (status 35). */
    record Inputs(List<String> tcatbal, Map<String, String> xrefByAcct, Map<String, String> discgrp,
                  Map<String, String> accounts) {}

    record Result(List<String> transactions, Map<String, String> accounts) {}

    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private final PrintStream sysout;
    private final Clock clock;

    InterestCalc(PrintStream sysout, Clock clock) {
        this.sysout = sysout;
        this.clock = clock;
    }

    Result run(Inputs in, String parm) {
        String parmDate = (parm + " ".repeat(10)).substring(0, 10);
        sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
        if (in.tcatbal() == null) {
            openError("ERROR OPENING TRANSACTION CATEGORY BALANCE");
        }
        if (in.xrefByAcct() == null) {
            openError("ERROR OPENING CROSS REF FILE" + "35");
        }
        if (in.discgrp() == null) {
            openError("ERROR OPENING DALY REJECTS FILE");
        }
        if (in.accounts() == null) {
            openError("ERROR OPENING ACCOUNT MASTER FILE");
        }

        Map<String, String> accounts = new TreeMap<>(in.accounts());
        List<String> transactions = new ArrayList<>();
        List<String> tcat = new ArrayList<>(in.tcatbal());
        tcat.sort(null); // KSDS order: ascending key (cols 1-17); stable tail keeps duplicates deterministic

        String lastAcct = " ".repeat(11);
        boolean firstTime = true;
        int suffix = 0;
        BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
        String acct = null;
        String card = null;

        for (String rec : tcat) {
            sysout.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!firstTime) {
                    update(accounts, lastAcct, totalInt);
                } else {
                    firstTime = false;
                }
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    notFound(acctId, "ERROR READING ACCOUNT FILE");
                }
                card = in.xrefByAcct().get(acctId);
                if (card == null) {
                    notFound(acctId, "ERROR READING XREF FILE");
                }
            }
            String groupId = acct.substring(112, 122);
            String typeCat = rec.substring(11, 17);
            BigDecimal rate = rate(in.discgrp(), groupId + typeCat);
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28));
                BigDecimal monthly = Zoned.store(bal.multiply(rate).divide(TWELVE_HUNDRED, 10,
                        java.math.RoundingMode.DOWN), 9);
                totalInt = Zoned.store(totalInt.add(monthly), 9);
                suffix = (suffix + 1) % 1_000_000;
                transactions.add(transaction(parmDate, suffix, acctId, monthly, card));
                // 1400-COMPUTE-FEES is empty in the legacy program (F4)
            }
        }
        // F1: the final account is never updated.
        sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");
        return new Result(transactions, accounts);
    }

    private BigDecimal rate(Map<String, String> discgrp, String key) {
        String row = discgrp.get(key);
        if (row == null) {
            sysout.println("DISCLOSURE GROUP RECORD MISSING");
            sysout.println("TRY WITH DEFAULT GROUP CODE");
            row = discgrp.get("DEFAULT   " + key.substring(10));
            if (row == null) {
                sysout.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                sysout.println("FILE STATUS IS: NNNN0023");
                abend();
            }
        }
        return Zoned.decode(row.substring(16, 22));
    }

    private void update(Map<String, String> accounts, String acctId, BigDecimal totalInt) {
        String a = accounts.get(acctId);
        BigDecimal bal = Zoned.decode(a.substring(12, 24));
        String zero = Zoned.encode(BigDecimal.ZERO, 10);
        String patched = a.substring(0, 12) + Zoned.encode(bal.add(totalInt), 10)
                + a.substring(24, 78) + zero + zero + a.substring(102);
        accounts.put(acctId, patched);
    }

    private String transaction(String parmDate, int suffix, String acctId, BigDecimal amt, String card) {
        LocalDateTime t = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        StringBuilder sb = new StringBuilder(350);
        sb.append(parmDate).append(String.format("%06d", suffix));
        sb.append("01").append("0005").append(pad("System", 10));
        sb.append(pad("Int. for a/c " + acctId, 100));
        sb.append(Zoned.encode(amt, 9));
        sb.append("000000000").append(" ".repeat(50 + 50 + 10));
        sb.append(card).append(ts).append(ts).append(" ".repeat(20));
        return sb.toString();
    }

    private static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    private void openError(String msg) {
        sysout.println(msg);
        sysout.println("FILE STATUS IS: NNNN0035");
        abend();
    }

    private void notFound(String acctId, String msg) {
        sysout.println("ACCOUNT NOT FOUND: " + acctId);
        sysout.println(msg);
        sysout.println("FILE STATUS IS: NNNN0023");
        abend();
    }

    private void abend() {
        sysout.println("ABENDING PROGRAM");
        throw new Abend("U0999");
    }
}
