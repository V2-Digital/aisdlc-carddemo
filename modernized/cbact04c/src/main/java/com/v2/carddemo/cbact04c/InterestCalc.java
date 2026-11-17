package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** CBACT04C (interest calculation), rules R1-R9 of the spec. Pure: lines in, lines out. */
public final class InterestCalc {
    public static final int TCATBAL_LRECL = 50;
    public static final int XREF_LRECL = 50;
    public static final int ACCT_LRECL = 300;
    public static final int DISCGRP_LRECL = 50;
    public static final int TRAN_LRECL = 350;

    private static final BigDecimal DIVISOR = BigDecimal.valueOf(1200);

    /** Outputs of a successful run. {@code stdout} is the DISPLAY output. */
    public record Result(List<String> transact, List<String> acctfile, List<String> stdout) {}

    private final String parm;
    private final LocalDateTime clock;
    private final int hundredths;
    private final List<String> out = new ArrayList<>();

    /** @param hundredths hundredths of a second (the clock has no sub-second field of that size) */
    public InterestCalc(String parm, LocalDateTime clock, int hundredths) {
        this.parm = Records.pad(parm, 10);
        this.clock = clock;
        this.hundredths = hundredths;
    }

    /** Runs the job; on {@link Abend} the DISPLAY lines so far are in {@link Abend#getMessage()}'s companion {@link #stdout()}. */
    public Result run(List<String> tcatbal, List<String> xref, List<String> acct, List<String> discgrp) {
        out.add("START OF EXECUTION OF PROGRAM CBACT04C");

        Map<String, String> xrefByAcct = new HashMap<>();
        for (String x : xref) {
            xrefByAcct.putIfAbsent(x.substring(25, 36), x.substring(0, 16));
        }
        Map<String, String> accounts = new TreeMap<>();
        for (String a : acct) {
            accounts.put(a.substring(0, 11), a);
        }
        Map<String, BigDecimal> rates = new HashMap<>();
        for (String d : discgrp) {
            rates.put(d.substring(0, 16), Zoned.parse(d.substring(16, 22)));
        }

        List<String> transact = new ArrayList<>();
        int suffix = 0;
        String lastAcct = null;
        String account = null;
        String card = null;
        BigDecimal total = BigDecimal.ZERO.setScale(2);

        for (String row : tcatbal) {
            out.add(row);
            String acctId = row.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (lastAcct != null) {
                    accounts.put(lastAcct, updateAccount(account, total));
                }
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                account = accounts.get(acctId);
                if (account == null) {
                    out.add("ACCOUNT NOT FOUND: " + acctId);
                    out.add("ERROR READING ACCOUNT FILE");
                    throw abend();
                }
                card = xrefByAcct.get(acctId);
                if (card == null) {
                    out.add("ACCOUNT NOT FOUND: " + acctId);
                    out.add("ERROR READING XREF FILE");
                    throw abend();
                }
            }
            String type = row.substring(11, 13);
            String cat = row.substring(13, 17);
            BigDecimal bal = Zoned.parse(row.substring(17, 28));

            BigDecimal rate = rates.get(account.substring(112, 122) + type + cat);
            if (rate == null) {
                out.add("DISCLOSURE GROUP RECORD MISSING");
                out.add("TRY WITH DEFAULT GROUP CODE");
                rate = rates.get(Records.pad("DEFAULT", 10) + type + cat);
                if (rate == null) {
                    out.add("ERROR READING DEFAULT DISCLOSURE GROUP");
                    throw abend();
                }
            }
            if (rate.signum() != 0) {
                BigDecimal monthly = Zoned.truncate(
                        bal.multiply(rate).divide(DIVISOR, 2, RoundingMode.DOWN), 11);
                total = Zoned.truncate(total.add(monthly), 11);
                suffix = (suffix + 1) % 1_000_000;
                transact.add(transaction(suffix, acctId, monthly, card));
            }
        }
        // D1 (preserved): the last account is never rewritten.

        out.add("END OF EXECUTION OF PROGRAM CBACT04C");
        return new Result(transact, new ArrayList<>(accounts.values()), List.copyOf(out));
    }

    /** DISPLAY output so far (including after an {@link Abend}). */
    public List<String> stdout() {
        return List.copyOf(out);
    }

    private Abend abend() {
        out.add("ABENDING PROGRAM");
        return new Abend("ABEND U0999");
    }

    /** 1050-UPDATE-ACCOUNT. */
    private static String updateAccount(String rec, BigDecimal total) {
        BigDecimal bal = Zoned.truncate(Zoned.parse(rec.substring(12, 24)).add(total), 12);
        String zero = Zoned.format(BigDecimal.ZERO.setScale(2), 12);
        return rec.substring(0, 12) + Zoned.format(bal, 12) + rec.substring(24, 78) + zero + zero
                + rec.substring(102);
    }

    /** 1300-B-WRITE-TX. */
    private String transaction(int suffix, String acctId, BigDecimal monthly, String card) {
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", clock.getYear(),
                clock.getMonthValue(), clock.getDayOfMonth(), clock.getHour(), clock.getMinute(),
                clock.getSecond(), hundredths);
        return parm + String.format("%06d", suffix)
                + "01" + "0005" + Records.pad("System", 10)
                + Records.pad("Int. for a/c " + acctId, 100)
                + Zoned.format(monthly, 11)
                + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card + ts + ts + " ".repeat(20);
    }
}
