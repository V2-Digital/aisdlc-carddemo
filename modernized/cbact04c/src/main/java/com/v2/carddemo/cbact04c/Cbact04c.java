package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java 21 port of CBACT04C (interest calculation). Behaviour, defects included, mirrors the COBOL. */
public final class Cbact04c {
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);
    private static final String ZERO_AMT = "00000000000{";

    /** COBOL 9999-ABEND-PROGRAM. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    private final PrintStream out;
    private final Clock clock;

    private Cbact04c(PrintStream out, Clock clock) {
        this.out = out;
        this.clock = clock;
    }

    /** Runs the job; returns the process exit code (0, or 16 on abend). */
    public static int run(String parm, Path inDir, Path outDir, Clock clock, PrintStream out, PrintStream err) {
        Cbact04c job = new Cbact04c(out, clock);
        try {
            job.execute(parm, inDir, outDir);
            return 0;
        } catch (Abend e) {
            err.println("CEE3ABD: ABEND U0999");
            return 16;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void abend() {
        out.println("ABENDING PROGRAM");
        throw new Abend();
    }

    private void status(String s) {
        out.println("FILE STATUS IS: NNNN00" + s);
    }

    private List<String> open(Path inDir, String dd, int lrecl, String msg) throws IOException {
        Path f = inDir.resolve(dd + ".txt");
        if (!Files.isRegularFile(f)) {
            out.println(msg);
            status("35");
            abend();
        }
        return FixedFile.read(f, lrecl);
    }

    private void execute(String parm, Path inDir, Path outDir) throws IOException {
        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        // R1 load (open order as in the COBOL)
        List<String> tcatbal = open(inDir, "TCATBALF", 50, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xrefRecs;
        Path xf = inDir.resolve("XREFFILE.txt");
        if (!Files.isRegularFile(xf)) {
            out.println("ERROR OPENING CROSS REF FILE35");
            status("35");
            abend();
        }
        xrefRecs = FixedFile.read(xf, 50);
        List<String> discRecs = open(inDir, "DISCGRP", 50, "ERROR OPENING DALY REJECTS FILE");
        List<String> acctRecs = open(inDir, "ACCTFILE", 300, "ERROR OPENING ACCOUNT MASTER FILE");

        TreeMap<String, String> tcat = new TreeMap<>();
        tcatbal.forEach(r -> tcat.putIfAbsent(r.substring(0, 17), r));
        TreeMap<String, String> xrefByCard = new TreeMap<>();
        xrefRecs.forEach(r -> xrefByCard.putIfAbsent(r.substring(0, 16), r));
        Map<String, String> xrefByAcct = new TreeMap<>();
        xrefByCard.values().forEach(r -> xrefByAcct.putIfAbsent(r.substring(25, 36), r));
        Map<String, String> disc = new TreeMap<>();
        discRecs.forEach(r -> disc.putIfAbsent(r.substring(0, 16), r));
        TreeMap<String, String> accts = new TreeMap<>();
        acctRecs.forEach(r -> accts.putIfAbsent(r.substring(0, 11), r));

        String parmDate = (parm + " ".repeat(10)).substring(0, 10);
        List<String> transact = new ArrayList<>();
        int suffix = 0;
        String lastAcct = null;
        String acct = null;     // current ACCOUNT-RECORD
        String card = null;
        BigDecimal total = BigDecimal.ZERO.setScale(2);

        // R2 drive; EOF ends the loop without a final 1050 (F1)
        for (String rec : tcat.values()) {
            out.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (lastAcct != null) {
                    accts.put(lastAcct, update(acct, total));
                }
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accts.get(acctId);
                if (acct == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    out.println("ERROR READING ACCOUNT FILE");
                    status("23");
                    abend();
                }
                String x = xrefByAcct.get(acctId);
                if (x == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    out.println("ERROR READING XREF FILE");
                    status("23");
                    abend();
                }
                card = x.substring(0, 16);
            }
            // R4 rate
            String typeCat = rec.substring(11, 13) + rec.substring(13, 17);
            String groupId = acct.substring(112, 122);
            String d = disc.get(groupId + typeCat);
            if (d == null) {
                out.println("DISCLOSURE GROUP RECORD MISSING");
                out.println("TRY WITH DEFAULT GROUP CODE");
                d = disc.get("DEFAULT   " + typeCat);
                if (d == null) {
                    out.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                    status("23");
                    abend();
                }
            }
            BigDecimal rate = Zoned.decode(d.substring(16, 22), 2);
            if (rate.signum() != 0) {
                // R5 interest: truncated, S9(9)V99
                BigDecimal bal = Zoned.decode(rec.substring(17, 28), 2);
                BigDecimal monthly = Zoned.wrap(
                        bal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN), 11);
                total = Zoned.wrap(total.add(monthly), 11);
                suffix = (suffix + 1) % 1_000_000;
                transact.add(transaction(parmDate, suffix, acctId, monthly, card));
            }
        }
        // ACCTFILE is written back in key order, untouched rows unchanged
        FixedFile.write(outDir.resolve("ACCTFILE.txt"), accts.values());
        FixedFile.write(outDir.resolve("TRANSACT.txt"), transact);
        out.println("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    /** R7 / 1050-UPDATE-ACCOUNT. */
    private static String update(String acct, BigDecimal total) {
        BigDecimal bal = Zoned.decode(acct.substring(12, 24), 2);
        String newBal = Zoned.encode(bal.add(total), 12);
        return acct.substring(0, 12) + newBal + acct.substring(24, 78) + ZERO_AMT
                + ZERO_AMT + acct.substring(102);
    }

    /** R6 / 1300-B-WRITE-TX. */
    private String transaction(String parmDate, int suffix, String acctId, BigDecimal amt, String card) {
        LocalDateTime t = LocalDateTime.now(clock);
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        String desc = ("Int. for a/c " + acctId + " ".repeat(100)).substring(0, 100);
        return parmDate + String.format("%06d", suffix)
                + "01" + "0005" + "System    "
                + desc
                + Zoned.encode(amt, 11)
                + "000000000"
                + " ".repeat(110)
                + card
                + ts + ts
                + " ".repeat(20);
    }
}
