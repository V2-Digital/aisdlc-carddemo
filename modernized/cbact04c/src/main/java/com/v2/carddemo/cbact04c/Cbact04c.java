package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/** Java 21 rewrite of CBACT04C (INTCALC), like-for-like including legacy defects. */
public final class Cbact04c {
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private static final class Abend extends RuntimeException {
        Abend() {
            super("abend", null, false, false);
        }
    }

    private final Config cfg;
    private final java.io.PrintStream out;
    private final TreeMap<String, String> acctByKey = new TreeMap<>();
    private final TreeMap<String, String> xrefByAcct = new TreeMap<>();
    private final TreeMap<String, String> discByKey = new TreeMap<>();
    private final List<String> transactions = new ArrayList<>();
    private final String parmDate;
    private int tranSuffix;
    private BigDecimal totalInt = BigDecimal.ZERO;

    private Cbact04c(Config cfg) {
        this.cfg = cfg;
        this.out = cfg.sysout();
        this.parmDate = Records.pad(cfg.parm() == null ? "" : cfg.parm(), 10);
    }

    /** Runs the program; returns the return code (0, or 16 on abend). No output files are written on abend. */
    public static int run(Config cfg) {
        return new Cbact04c(cfg).execute();
    }

    private int execute() {
        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            List<String> tcatbal = openTcatbal();
            openXref();
            openDiscgrp();
            openAcct();
            process(tcatbal);
            Records.write(cfg.transact(), transactions);
            Records.write(cfg.acctfile(), new ArrayList<>(acctByKey.values()));
        } catch (Abend a) {
            out.println("ABENDING PROGRAM");
            cfg.syserr().println("CEE3ABD: ABEND U0999");
            return 16;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        out.println("END OF EXECUTION OF PROGRAM CBACT04C");
        return 0;
    }

    private List<String> openTcatbal() {
        List<String> recs = loadOrAbend(cfg.tcatbalf(), Records.TCATBAL_LEN, "TRANSACTION CATEGORY BALANCE FILE");
        Collections.sort(recs, (a, b) -> a.substring(0, 17).compareTo(b.substring(0, 17)));
        return recs;
    }

    private void openXref() {
        for (String r : loadOrAbend(cfg.xreffile(), Records.XREF_LEN, "XREF FILE")) {
            xrefByAcct.merge(r.substring(25, 36) + r.substring(0, 16), r, (a, b) -> a);
        }
    }

    private void openDiscgrp() {
        // D6: legacy prints the wrong text for a DISCGRP open error.
        for (String r : loadOrAbend(cfg.discgrp(), Records.DISCGRP_LEN, "DALY REJECTS FILE")) {
            discByKey.put(r.substring(0, 16), r);
        }
    }

    private void openAcct() {
        for (String r : loadOrAbend(cfg.acctfile(), Records.ACCT_LEN, "ACCOUNT FILE")) {
            acctByKey.put(r.substring(0, 11), r);
        }
    }

    private List<String> loadOrAbend(java.nio.file.Path p, int len, String what) {
        if (p == null || !Files.isReadable(p)) {
            out.println("ERROR OPENING " + what);
            out.println("FILE STATUS IS: NNNN0035");
            throw new Abend();
        }
        try {
            return Records.load(p, len);
        } catch (IOException e) {
            out.println("ERROR OPENING " + what);
            out.println("FILE STATUS IS: NNNN0030");
            throw new Abend();
        }
    }

    private void process(List<String> tcatbal) {
        String lastAcct = null;
        String acct = null; // current account record
        String card = null;
        for (String rec : tcatbal) {
            out.println(rec);
            String acctId = rec.substring(0, 11);
            if (lastAcct == null || !acctId.equals(lastAcct)) {
                if (lastAcct != null) {
                    updateAccount(acct);
                }
                totalInt = BigDecimal.ZERO;
                lastAcct = acctId;
                acct = acctByKey.get(acctId);
                if (acct == null) {
                    notFound(acctId, "ERROR READING ACCOUNT FILE");
                }
                card = null;
                for (var e : xrefByAcct.subMap(acctId, true, acctId + "\uffff", true).entrySet()) {
                    card = e.getValue().substring(0, 16);
                    break;
                }
                if (card == null) {
                    notFound(acctId, "ERROR READING XREF FILE");
                }
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            BigDecimal rate = lookupRate(acct.substring(112, 122), type, cat);
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28));
                BigDecimal monthly = Zoned.wrap(
                        bal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN), 9);
                totalInt = Zoned.wrap(totalInt.add(monthly), 9);
                writeTransaction(acctId, card, monthly);
                // 1400-COMPUTE-FEES: empty in legacy (D5)
            }
            // D1: the last account is never rewritten, as in legacy.
        }
    }

    private void notFound(String acctId, String msg) {
        out.println("ACCOUNT NOT FOUND: " + acctId);
        out.println(msg);
        out.println("FILE STATUS IS: NNNN0023");
        throw new Abend();
    }

    private BigDecimal lookupRate(String group, String type, String cat) {
        String r = discByKey.get(group + type + cat);
        if (r == null) {
            out.println("DISCLOSURE GROUP RECORD MISSING");
            out.println("TRY WITH DEFAULT GROUP CODE");
            r = discByKey.get("DEFAULT   " + type + cat);
            if (r == null) {
                out.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                out.println("FILE STATUS IS: NNNN0023");
                throw new Abend();
            }
        }
        return Zoned.decode(r.substring(16, 22));
    }

    private void writeTransaction(String acctId, String card, BigDecimal amt) {
        tranSuffix = (tranSuffix + 1) % 1_000_000;
        LocalDateTime t = LocalDateTime.now(cfg.clock().withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        StringBuilder sb = new StringBuilder(" ".repeat(Records.TRAN_LEN));
        put(sb, 0, parmDate + String.format("%06d", tranSuffix));
        put(sb, 16, "01");
        put(sb, 18, "0005");
        put(sb, 22, "System");
        put(sb, 32, "Int. for a/c " + acctId);
        put(sb, 132, Zoned.encode(amt, 11));
        put(sb, 143, "000000000");
        put(sb, 262, card);
        put(sb, 278, ts);
        put(sb, 304, ts);
        transactions.add(sb.toString());
    }

    private void updateAccount(String acct) {
        BigDecimal bal = Zoned.wrap(Zoned.decode(acct.substring(12, 24)).add(totalInt), 10);
        StringBuilder sb = new StringBuilder(acct);
        put(sb, 12, Zoned.encode(bal, 12));
        put(sb, 78, "00000000000{");
        put(sb, 90, "00000000000{");
        acctByKey.put(acct.substring(0, 11), sb.toString());
    }

    private static void put(StringBuilder sb, int off, String s) {
        sb.replace(off, off + s.length(), s);
    }
}
