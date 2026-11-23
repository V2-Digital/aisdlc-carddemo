package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Behaviour-preserving port of CBACT04C (nightly interest calculation). */
public final class InterestCalc {
    private static final BigDecimal DIVISOR = BigDecimal.valueOf(1200);

    private final String parmDate;
    private final Supplier<String> clock; // 16 digits, YYYYMMDDHHMMSSss
    private final Consumer<String> sysout;

    private final List<String> tcatbal;
    private final Map<String, String> xrefByAcct = new HashMap<>(); // acct-id -> first card (primary-key order)
    private final Map<String, String> discgrp = new HashMap<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();
    private final List<String> transactions = new ArrayList<>();

    /** A null list means the dataset could not be opened (file status 35). */
    public InterestCalc(String parm, Supplier<String> clock, Consumer<String> sysout,
                        List<String> tcatbal, List<String> xref, List<String> discgrp, List<String> acct) {
        this.parmDate = Records.pad(parm == null ? "" : parm, 10).substring(0, 10);
        this.clock = clock;
        this.sysout = sysout;
        this.sysout.accept("START OF EXECUTION OF PROGRAM CBACT04C");
        this.tcatbal = open(tcatbal, "ERROR OPENING TRANSACTION CATEGORY BALANCE", "");
        List<String> x = open(xref, "ERROR OPENING CROSS REF FILE", "35");
        List<String> d = open(discgrp, "ERROR OPENING DALY REJECTS FILE", "");
        List<String> a = open(acct, "ERROR OPENING ACCOUNT MASTER FILE", "");

        this.tcatbal.sort((p, q) -> Records.col(p, 1, 17).compareTo(Records.col(q, 1, 17)));
        TreeMap<String, String> byCard = new TreeMap<>();
        x.forEach(r -> byCard.put(Records.col(r, 1, 16), r));
        byCard.values().forEach(r -> xrefByAcct.putIfAbsent(Records.col(r, 26, 36), r));
        d.forEach(r -> this.discgrp.put(Records.col(r, 1, 16), r));
        a.forEach(r -> accounts.put(Records.col(r, 1, 11), r));
    }

    private <T> List<String> open(List<String> file, String message, String statusSuffix) {
        if (file == null) {
            sysout.accept(message + statusSuffix);
            ioStatus("35");
            throw abend();
        }
        return new ArrayList<>(file);
    }

    public List<String> transactions() {
        return transactions;
    }

    /** Account master in primary-key order. */
    public List<String> accounts() {
        return new ArrayList<>(accounts.values());
    }

    /** Runs the main loop; throws {@link Abend} on any legacy abend (outputs keep partial state). */
    public void run() {
        String lastAcct = " ".repeat(11);
        boolean first = true;
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        String acct = null;
        String cardNum = null;
        int suffix = 0;

        for (String rec : tcatbal) {
            sysout.accept(rec);
            String acctId = Records.col(rec, 1, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    update(acct, total);
                } else {
                    first = false;
                }
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    sysout.accept("ACCOUNT NOT FOUND: " + acctId);
                    sysout.accept("ERROR READING ACCOUNT FILE");
                    ioStatus("23");
                    throw abend();
                }
                String xref = xrefByAcct.get(acctId);
                if (xref == null) {
                    sysout.accept("ACCOUNT NOT FOUND: " + acctId);
                    sysout.accept("ERROR READING XREF FILE");
                    ioStatus("23");
                    throw abend();
                }
                cardNum = Records.col(xref, 1, 16);
            }
            String type = Records.col(rec, 12, 13);
            String cat = Records.col(rec, 14, 17);
            String group = Records.col(acct, 113, 122);
            String dis = discgrp.get(group + type + cat);
            if (dis == null) {
                sysout.accept("DISCLOSURE GROUP RECORD MISSING");
                sysout.accept("TRY WITH DEFAULT GROUP CODE");
                dis = discgrp.get("DEFAULT   " + type + cat);
                if (dis == null) {
                    sysout.accept("ERROR READING DEFAULT DISCLOSURE GROUP");
                    ioStatus("23");
                    throw abend();
                }
            }
            BigDecimal rate = Zoned.decode(Records.col(dis, 17, 22), 2);
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(Records.col(rec, 18, 28), 2);
                BigDecimal interest = Zoned.truncate(
                        bal.multiply(rate).divide(DIVISOR, 2, RoundingMode.DOWN), 11, 2);
                total = Zoned.truncate(total.add(interest), 11, 2);
                suffix = (suffix + 1) % 1_000_000;
                transactions.add(transaction(suffix, acctId, interest, cardNum));
                // 1400-COMPUTE-FEES is an empty paragraph in the legacy program (F2)
            }
        }
        // F1: the last account is never updated; the legacy ELSE branch is unreachable.
        sysout.accept("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private void update(String acct, BigDecimal total) {
        BigDecimal bal = Zoned.decode(Records.col(acct, 13, 24), 2);
        String rec = Records.put(acct, 13, Zoned.encode(bal.add(total), 12, 2));
        rec = Records.put(rec, 79, Zoned.encode(BigDecimal.ZERO, 12, 2));
        rec = Records.put(rec, 91, Zoned.encode(BigDecimal.ZERO, 12, 2));
        accounts.put(Records.col(rec, 1, 11), rec);
    }

    private String transaction(int suffix, String acctId, BigDecimal amount, String cardNum) {
        String ts = timestamp();
        String desc = Records.pad("Int. for a/c " + acctId, 100);
        return parmDate + String.format("%06d", suffix)   // 1-16 id
                + "01" + "0005"                             // type, cat
                + Records.pad("System", 10)                 // source
                + desc                                      // 33-132
                + Zoned.encode(amount, 11, 2)               // 133-143
                + "000000000"                               // merchant id
                + " ".repeat(110)                           // merchant name, city, zip
                + cardNum + ts + ts                         // card, orig-ts, proc-ts
                + " ".repeat(20);
    }

    /** YYYY-MM-DD-HH.MM.SS.hh0000 from a 16-digit YYYYMMDDHHMMSSss clock. */
    private String timestamp() {
        String c = clock.get();
        return c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
                + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14) + "."
                + c.substring(14, 16) + "0000";
    }

    private void ioStatus(String status) {
        sysout.accept("FILE STATUS IS: NNNN00" + status);
    }

    private Abend abend() {
        sysout.accept("ABENDING PROGRAM");
        return new Abend("CEE3ABD: ABEND U0999");
    }
}
