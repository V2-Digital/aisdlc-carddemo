package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Interest calculation batch, a like-for-like port of CBACT04C (defects preserved). */
public final class Cbact04c {
    private static final BigDecimal MONTHS_X_100 = BigDecimal.valueOf(1200);

    private final Clock clock;
    private final PrintStream out;

    /** Raised where the COBOL program performs 9999-ABEND-PROGRAM. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    public Cbact04c(Clock clock, PrintStream out) {
        this.clock = clock;
        this.out = out;
    }

    /** Runs the job and returns the program RC (0, or 16 on abend). */
    public int run(String parm, Path inDir, Path outDir) throws IOException {
        String parmDate = Records.pad(parm, 10);
        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            execute(parmDate, inDir, outDir);
        } catch (Abend a) {
            out.println("ABENDING PROGRAM");
            return 16;
        }
        out.println("END OF EXECUTION OF PROGRAM CBACT04C");
        return 0;
    }

    private void execute(String parmDate, Path inDir, Path outDir) throws IOException {
        List<String> tcatbal = open(inDir, "TCATBALF", Records.TCATBALF,
                "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xref = open(inDir, "XREFFILE", Records.XREFFILE, "ERROR OPENING CROSS REF FILE");
        List<String> discgrp = open(inDir, "DISCGRP", Records.DISCGRP, "ERROR OPENING DALY REJECTS FILE");
        List<String> acctRecs = open(inDir, "ACCTFILE", Records.ACCTFILE, "ERROR OPENING ACCOUNT MASTER FILE");

        Map<String, String> rates = new HashMap<>();
        for (String r : discgrp) {
            rates.putIfAbsent(Records.field(r, 1, 16), r);
        }
        TreeMap<String, String> accounts = new TreeMap<>();
        for (String r : acctRecs) {
            accounts.put(Records.field(r, 1, 11), r);
        }
        List<String> transactions = new ArrayList<>();

        int suffix = 0;
        boolean first = true;
        String lastAcct = "";
        String account = null;
        String cardNum = null;
        BigDecimal total = BigDecimal.ZERO.setScale(2);

        for (String rec : tcatbal) {
            out.println(rec);
            String acctId = Records.field(rec, 1, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    rewrite(accounts, account, total);
                } else {
                    first = false;
                }
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                account = accounts.get(acctId);
                if (account == null) {
                    notFound(acctId, "ERROR READING ACCOUNT FILE");
                }
                cardNum = null;
                for (String x : xref) {
                    if (Records.field(x, 26, 11).equals(acctId)) {
                        cardNum = Records.field(x, 1, 16);
                        break;
                    }
                }
                if (cardNum == null) {
                    notFound(acctId, "ERROR READING XREF FILE");
                }
            }

            String group = Records.field(account, 113, 10);
            String typeCat = Records.field(rec, 12, 2) + Records.field(rec, 14, 4);
            String dis = rates.get(group + typeCat);
            if (dis == null) {
                out.println("DISCLOSURE GROUP RECORD MISSING");
                out.println("TRY WITH DEFAULT GROUP CODE");
                dis = rates.get("DEFAULT   " + typeCat);
                if (dis == null) {
                    out.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                    out.println("FILE STATUS IS: NNNN0023");
                    throw new Abend();
                }
            }
            BigDecimal rate = Zoned.parse(Records.field(dis, 17, 6));
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.parse(Records.field(rec, 18, 11));
                BigDecimal monthly = Zoned.trunc(
                        bal.multiply(rate).divide(MONTHS_X_100, 2, java.math.RoundingMode.DOWN), 9);
                total = Zoned.trunc(total.add(monthly), 9);
                suffix = (suffix + 1) % 1_000_000;
                transactions.add(transaction(parmDate, suffix, acctId, monthly, cardNum));
                // 1400-COMPUTE-FEES is empty in the legacy program (D2).
            }
        }
        // The last account is never rewritten: the legacy ELSE branch is unreachable (D1).

        Records.write(outDir, "TRANSACT", transactions);
        Records.write(outDir, "ACCTFILE", new ArrayList<>(accounts.values()));
    }

    private List<String> open(Path dir, String dd, int lrecl, String message) throws IOException {
        try {
            return Records.read(dir, dd, lrecl);
        } catch (NoSuchFileException e) {
            out.println(message);
            out.println("FILE STATUS IS: NNNN0035");
            throw new Abend();
        }
    }

    private void notFound(String acctId, String message) {
        out.println("ACCOUNT NOT FOUND: " + acctId);
        out.println(message);
        out.println("FILE STATUS IS: NNNN0023");
        throw new Abend();
    }

    /** 1050-UPDATE-ACCOUNT: the in-memory record replaces the stored one. */
    private static void rewrite(TreeMap<String, String> accounts, String account, BigDecimal total) {
        BigDecimal bal = Zoned.parse(Records.field(account, 13, 12));
        String updated = Records.put(account, 13, Zoned.format(Zoned.trunc(bal.add(total), 10), 10));
        String zero = Zoned.format(BigDecimal.ZERO, 10);
        updated = Records.put(updated, 79, zero);
        updated = Records.put(updated, 91, zero);
        accounts.put(Records.field(updated, 1, 11), updated);
    }

    private String transaction(String parmDate, int suffix, String acctId, BigDecimal amt, String card) {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        return parmDate + String.format("%06d", suffix)
                + "01" + "0005" + Records.pad("System", 10)
                + Records.pad("Int. for a/c " + acctId, 100)
                + Zoned.format(amt, 9)
                + "000000000" + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card + ts + ts + " ".repeat(20);
    }
}
