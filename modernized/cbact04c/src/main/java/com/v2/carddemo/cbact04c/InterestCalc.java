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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java port of CBACT04C (INTCALC), behaviour-preserving including legacy defects. */
public final class InterestCalc {
    static final int TCAT_LEN = 50, XREF_LEN = 50, ACCT_LEN = 300, DISC_LEN = 50, TRAN_LEN = 350;

    /** CEE3ABD U0999; the harness maps it to RC 16. */
    static final class Abend extends RuntimeException {
        Abend() { super("ABEND U0999"); }
    }

    private final Clock clock;
    private final PrintStream sysout;

    private final List<String> transactions = new ArrayList<>();
    private final Map<String, String> accounts = new TreeMap<>();

    public InterestCalc(Clock clock, PrintStream sysout) {
        this.clock = clock;
        this.sysout = sysout;
    }

    /** Runs the job; returns the RC. Outputs are written even on abend (no rollback). */
    public int run(String parm, Path inDir, Path outDir) throws IOException {
        String parmDate = RecordFile.pad(parm, 10);
        int rc = 0;
        try {
            execute(parmDate, inDir);
        } catch (Abend a) {
            rc = 16;
        }
        RecordFile.write(outDir.resolve("TRANSACT.txt"), transactions);
        RecordFile.write(outDir.resolve("ACCTFILE.txt"), accounts.values());
        return rc;
    }

    private List<String> open(Path inDir, String dd, int lrecl, String message) throws IOException {
        Path f = inDir.resolve(dd + ".txt");
        if (!Files.isReadable(f)) {
            sysout.println(message);
            sysout.println("FILE STATUS IS: NNNN0035");
            throw abend();
        }
        return RecordFile.read(f, lrecl);
    }

    private Abend abend() {
        sysout.println("ABENDING PROGRAM");
        return new Abend();
    }

    private void execute(String parmDate, Path inDir) throws IOException {
        sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcat = open(inDir, "TCATBALF", TCAT_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xrefRecs = open(inDir, "XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE");
        List<String> discRecs = open(inDir, "DISCGRP", DISC_LEN, "ERROR OPENING DALY REJECTS FILE");
        List<String> acctRecs = open(inDir, "ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE");

        Map<String, String> xrefByAcct = new HashMap<>();
        for (String x : xrefRecs) {
            xrefByAcct.putIfAbsent(x.substring(25, 36), x);
        }
        Map<String, BigDecimal> rates = new HashMap<>();
        for (String d : discRecs) {
            rates.put(d.substring(0, 16), Zoned.decode(d.substring(16, 22)));
        }
        for (String a : acctRecs) {
            accounts.put(a.substring(0, 11), a);
        }

        String lastAcct = " ".repeat(11);
        boolean first = true;
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        String acct = null;
        String card = null;
        int suffix = 0;

        for (String rec : tcat) {
            sysout.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(acct, total);
                } else {
                    first = false;
                }
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    sysout.println("ACCOUNT NOT FOUND: " + acctId);
                    sysout.println("ERROR READING ACCOUNT FILE");
                    sysout.println("FILE STATUS IS: NNNN0023");
                    throw abend();
                }
                String xref = xrefByAcct.get(acctId);
                if (xref == null) {
                    sysout.println("ACCOUNT NOT FOUND: " + acctId);
                    sysout.println("ERROR READING XREF FILE");
                    sysout.println("FILE STATUS IS: NNNN0023");
                    throw abend();
                }
                card = xref.substring(0, 16);
            }
            String typeCat = rec.substring(11, 17);
            BigDecimal rate = rates.get(acct.substring(112, 122) + typeCat);
            if (rate == null) {
                sysout.println("DISCLOSURE GROUP RECORD MISSING");
                sysout.println("TRY WITH DEFAULT GROUP CODE");
                rate = rates.get(RecordFile.pad("DEFAULT", 10) + typeCat);
                if (rate == null) {
                    sysout.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                    sysout.println("FILE STATUS IS: NNNN0023");
                    throw abend();
                }
            }
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28));
                BigDecimal monthly = Zoned.wrap(
                        bal.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 11);
                total = Zoned.wrap(total.add(monthly), 11);
                suffix = (suffix + 1) % 1_000_000;
                transactions.add(transaction(parmDate, suffix, acct, monthly, card));
            }
        }
        // F1: the legacy loop never rewrites the last account.
        sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private void updateAccount(String acct, BigDecimal total) {
        BigDecimal bal = Zoned.decode(acct.substring(12, 24));
        BigDecimal newBal = Zoned.wrap(bal.add(total), 12);
        String zero = Zoned.encode(BigDecimal.ZERO.setScale(2), 12);
        String updated = acct.substring(0, 12) + Zoned.encode(newBal, 12)
                + acct.substring(24, 78) + zero + zero + acct.substring(102);
        accounts.put(acct.substring(0, 11), updated);
    }

    private String transaction(String parmDate, int suffix, String acct, BigDecimal amt, String card) {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        StringBuilder sb = new StringBuilder(TRAN_LEN);
        sb.append(parmDate).append(String.format("%06d", suffix));
        sb.append("01").append("0005").append(RecordFile.pad("System", 10));
        sb.append(RecordFile.pad("Int. for a/c " + acct.substring(0, 11), 100));
        sb.append(Zoned.encode(amt, 11));
        sb.append("000000000").append(" ".repeat(110));
        sb.append(card).append(ts).append(ts).append(" ".repeat(20));
        return sb.toString();
    }
}
