package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java rewrite of CBACT04C (INTCALC): monthly interest transactions and account update. */
public final class Cbact04c {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int DISC_LEN = 50;
    static final int TRAN_LEN = 350;

    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private final Path inDir;
    private final Path outDir;
    private final String parm;
    private final Clock clock;
    private final PrintStream out;
    private final PrintStream err;

    private int suffix;
    private BigDecimal totalInt = BigDecimal.ZERO.setScale(2);

    /** CEE3ABD U0999. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    public Cbact04c(Path inDir, Path outDir, String parm, Clock clock, PrintStream out, PrintStream err) {
        this.inDir = inDir;
        this.outDir = outDir;
        this.parm = (parm + " ".repeat(10)).substring(0, 10); // F6: always 10 bytes
        this.clock = clock;
        this.out = out;
        this.err = err;
    }

    /** Runs the job; returns the return code. Output files are only written when it is 0. */
    public int run() {
        try {
            execute();
            return 0;
        } catch (Abend e) {
            show("ABENDING PROGRAM");
            err.print("CEE3ABD: ABEND U0999\n");
            return 16;
        }
    }

    private void execute() {
        show("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcatbalRecs = open("TCATBALF", TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xrefRecs = open("XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE35");
        List<String> discRecs = open("DISCGRP", DISC_LEN, "ERROR OPENING DALY REJECTS FILE");
        List<String> acctRecs = open("ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE");

        Map<String, String> tcatbal = new TreeMap<>();
        Map<String, String> xref = new TreeMap<>();
        Map<String, String> disc = new TreeMap<>();
        Map<String, String> accts = new TreeMap<>();
        Datasets.index(tcatbalRecs, 0, 17, tcatbal);
        Datasets.index(xrefRecs, 25, 11, xref); // alt key: first duplicate wins (F7)
        Datasets.index(discRecs, 0, 16, disc);
        Datasets.index(acctRecs, 0, 11, accts);

        List<String> trans = new ArrayList<>();
        String lastAcct = "           ";
        boolean first = true;
        String acct = null;
        String card = null;

        for (String rec : tcatbal.values()) {
            show(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(accts, acct);
                } else {
                    first = false;
                }
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accts.get(acctId);
                if (acct == null) {
                    show("ACCOUNT NOT FOUND: " + acctId);
                    abendStatus("ERROR READING ACCOUNT FILE");
                }
                String x = xref.get(acctId);
                if (x == null) {
                    show("ACCOUNT NOT FOUND: " + acctId);
                    abendStatus("ERROR READING XREF FILE");
                }
                card = x.substring(0, 16);
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            String d = disc.get(acct.substring(112, 122) + type + cat);
            if (d == null) {
                show("DISCLOSURE GROUP RECORD MISSING");
                show("TRY WITH DEFAULT GROUP CODE");
                d = disc.get("DEFAULT   " + type + cat);
                if (d == null) {
                    abendStatus("ERROR READING DEFAULT DISCLOSURE GROUP");
                }
            }
            BigDecimal rate = decode(d.substring(16, 22));
            if (rate.signum() != 0) {
                BigDecimal bal = decode(rec.substring(17, 28));
                trans.add(interest(bal, rate, acct.substring(0, 11), card));
                // 1400-COMPUTE-FEES: not implemented in the legacy program (CR-2)
            }
        }
        // F1: the last account is never rewritten (the legacy loop exits before its ELSE branch).

        show("END OF EXECUTION OF PROGRAM CBACT04C");
        try {
            Files.createDirectories(outDir);
            Datasets.write(outDir.resolve("ACCTFILE.txt"), accts.values());
            Datasets.write(outDir.resolve("TRANSACT.txt"), trans);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private List<String> open(String dd, int lrecl, String message) {
        try {
            return Datasets.read(inDir.resolve(dd + ".txt"), lrecl);
        } catch (IOException e) {
            show(message);
            abendStatus(null);
            return null;
        }
    }

    private BigDecimal decode(String field) {
        try {
            return Zoned.decode(field, 2);
        } catch (IllegalArgumentException e) {
            throw new Abend();
        }
    }

    /** 1300-COMPUTE-INTEREST + 1300-B-WRITE-TX; returns the TRANSACT record. */
    private String interest(BigDecimal bal, BigDecimal rate, String acctId, String card) {
        // No ROUNDED: truncate toward zero (F2); no SIZE ERROR: drop high-order digits (F5).
        BigDecimal monthly = Zoned.truncate(
                bal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN), 9);
        totalInt = Zoned.truncate(totalInt.add(monthly), 9);
        suffix = (suffix + 1) % 1_000_000;
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        return pad(parm + String.format("%06d", suffix)
                + "01" + "0005" + pad("System", 10) + pad("Int. for a/c " + acctId, 100)
                + Zoned.encode(monthly, 11) + "000000000" + " ".repeat(110) + card + ts + ts, TRAN_LEN);
    }

    /** 1050-UPDATE-ACCOUNT. */
    private void updateAccount(Map<String, String> accts, String acct) {
        BigDecimal bal = decode(acct.substring(12, 24));
        bal = Zoned.truncate(bal.add(totalInt), 10);
        String zero = Zoned.encode(BigDecimal.ZERO.setScale(2), 12);
        String updated = acct.substring(0, 12) + Zoned.encode(bal, 12) + acct.substring(24, 78) + zero
                + zero + acct.substring(102);
        accts.put(acct.substring(0, 11), updated);
    }

    private void abendStatus(String message) {
        if (message != null) {
            show(message);
        }
        show("FILE STATUS IS: NNNN" + (message == null ? "0035" : "0023"));
        throw new Abend();
    }

    private static String pad(String s, int len) {
        return (s + " ".repeat(len)).substring(0, len);
    }

    private void show(String line) {
        out.print(line + "\n");
    }
}
