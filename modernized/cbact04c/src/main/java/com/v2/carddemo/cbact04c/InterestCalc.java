package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Like-for-like port of CBACT04C (INTCALC). Legacy defects are preserved on purpose:
 * D1 last account never rewritten, D2 truncation, D3 no fees, D4 silent overflow.
 */
public final class InterestCalc {
    public static final int TCATBAL_LRECL = 50;
    public static final int XREF_LRECL = 50;
    public static final int ACCT_LRECL = 300;
    public static final int DISCGRP_LRECL = 50;
    public static final int TRAN_LRECL = 350;

    private static final BigDecimal MONTHS_X_100 = new BigDecimal(1200);

    /** Output datasets and exit code. */
    public record Result(List<String> transact, List<String> acctfile, int rc) {}

    private final Supplier<LocalDateTime> clock;
    private final PrintStream out;

    public InterestCalc(Supplier<LocalDateTime> clock, PrintStream out) {
        this.clock = clock;
        this.out = out;
    }

    /**
     * @param inputs records by DD name (TCATBALF, XREFFILE, ACCTFILE, DISCGRP); a missing entry is an open error
     */
    public Result run(String parmDate, Map<String, List<String>> inputs) {
        String parm = RecordFiles.pad(parmDate, 10);
        List<String> transact = new ArrayList<>();
        TreeMap<String, String> accounts = new TreeMap<>();
        int rc = 0;
        display("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            open(inputs, "TCATBALF", "ERROR OPENING TRANSACTION CATEGORY BALANCE");
            open(inputs, "XREFFILE", "ERROR OPENING CROSS REF FILE35");
            open(inputs, "DISCGRP", "ERROR OPENING DALY REJECTS FILE");
            open(inputs, "ACCTFILE", "ERROR OPENING ACCOUNT MASTER FILE");
            for (String a : inputs.get("ACCTFILE")) {
                accounts.put(a.substring(0, 11), a);
            }
            process(parm, inputs, accounts, transact);
            display("END OF EXECUTION OF PROGRAM CBACT04C");
        } catch (AbendException e) {
            display("ABENDING PROGRAM");
            System.err.println("CEE3ABD: ABEND U0999");
            rc = 16;
        }
        return new Result(transact, new ArrayList<>(accounts.values()), rc);
    }

    private void open(Map<String, List<String>> inputs, String dd, String message) {
        if (inputs.get(dd) == null) {
            display(message);
            ioStatus("35");
            throw new AbendException("35");
        }
    }

    private void process(String parm, Map<String, List<String>> inputs,
                         TreeMap<String, String> accounts, List<String> transact) {
        List<String> tcatbal = new ArrayList<>(inputs.get("TCATBALF"));
        tcatbal.sort(Comparator.comparing(r -> r.substring(0, 17)));

        // XREFFILE alternate key (acct-id) is non-unique: the lowest card number wins (R2).
        Map<String, String> xrefByAcct = new HashMap<>();
        List<String> xrefs = new ArrayList<>(inputs.get("XREFFILE"));
        xrefs.sort(Comparator.comparing(r -> r.substring(0, 16)));
        for (String x : xrefs) {
            xrefByAcct.putIfAbsent(x.substring(25, 36), x);
        }
        Map<String, String> discgrp = new HashMap<>();
        for (String d : inputs.get("DISCGRP")) {
            discgrp.putIfAbsent(d.substring(0, 16), d);
        }

        String lastAcct = "";
        boolean first = true;
        String acct = null;
        String xref = null;
        BigDecimal totalInt = BigDecimal.ZERO;
        int tranSuffix = 0;

        for (String rec : tcatbal) {
            display(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    accounts.put(acct.substring(0, 11), updateAccount(acct, totalInt));
                }
                first = false;
                totalInt = BigDecimal.ZERO;
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING ACCOUNT FILE");
                    ioStatus("23");
                    throw new AbendException("23");
                }
                xref = xrefByAcct.get(acctId);
                if (xref == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING XREF FILE");
                    ioStatus("23");
                    throw new AbendException("23");
                }
            }

            String group = acct.substring(112, 122);
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            String dis = discgrp.get(group + type + cat);
            if (dis == null) {
                display("DISCLOSURE GROUP RECORD MISSING");
                display("TRY WITH DEFAULT GROUP CODE");
                dis = discgrp.get(RecordFiles.pad("DEFAULT", 10) + type + cat);
                if (dis == null) {
                    display("ERROR READING DEFAULT DISCLOSURE GROUP");
                    ioStatus("23");
                    throw new AbendException("23");
                }
            }
            BigDecimal rate = Zoned.parse(dis.substring(16, 22));
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.parse(rec.substring(17, 28));
                BigDecimal monthly = Zoned.truncate(
                        bal.multiply(rate).divide(MONTHS_X_100, 10, RoundingMode.DOWN)
                                .setScale(2, RoundingMode.DOWN), 9);
                totalInt = Zoned.truncate(totalInt.add(monthly), 9);
                tranSuffix = (tranSuffix + 1) % 1_000_000;
                transact.add(transaction(parm, tranSuffix, acctId, monthly, xref.substring(0, 16)));
                // 1400-COMPUTE-FEES: empty in legacy (D3)
            }
        }
        // D1: no update of the last account here; the legacy ELSE branch is unreachable.
    }

    private static String updateAccount(String acct, BigDecimal totalInt) {
        BigDecimal bal = Zoned.parse(acct.substring(12, 24));
        String newBal = Zoned.format(Zoned.truncate(bal.add(totalInt), 10), 12);
        String zero = Zoned.format(BigDecimal.ZERO, 12);
        return acct.substring(0, 12) + newBal + acct.substring(24, 78) + zero + zero + acct.substring(102);
    }

    private String transaction(String parm, int suffix, String acctId, BigDecimal amt, String card) {
        char[] t = " ".repeat(TRAN_LRECL).toCharArray();
        put(t, 1, parm + String.format("%06d", suffix));
        put(t, 17, "01");
        put(t, 19, "0005");
        put(t, 23, "System");
        put(t, 33, "Int. for a/c " + acctId);
        put(t, 133, Zoned.format(amt, 11));
        put(t, 144, "000000000");
        put(t, 263, card);
        String ts = timestamp();
        put(t, 279, ts);
        put(t, 305, ts);
        return new String(t);
    }

    private static void put(char[] rec, int col, String value) {
        value.getChars(0, value.length(), rec, col - 1);
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss");

    private String timestamp() {
        LocalDateTime now = clock.get();
        return TS.format(now) + String.format(".%02d0000", now.getNano() / 10_000_000);
    }

    private void ioStatus(String status) {
        display("FILE STATUS IS: NNNN00" + status);
    }

    private void display(String line) {
        out.print(line + "\n");
    }
}
