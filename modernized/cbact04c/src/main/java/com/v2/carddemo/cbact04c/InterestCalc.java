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

/**
 * Java rewrite of CBACT04C (interest calculation). Behaviour-preserving, defects included
 * (see README): last account is not rewritten, interest is truncated, overflow is silent.
 */
public final class InterestCalc {
    static final int TCAT_LEN = 50;
    static final int XREF_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int DISC_LEN = 50;
    static final int TRAN_LEN = 350;

    /** Program abend (CEE3ABD U0999); the harness reports exit code 16. */
    private static final class Abend extends Exception {
        Abend() {
            super("ABENDING PROGRAM", null, false, false);
        }
    }

    private final PrintStream sysout;
    private final PrintStream stderr;
    private final Clock clock;

    public InterestCalc(PrintStream sysout, PrintStream stderr, Clock clock) {
        this.sysout = sysout;
        this.stderr = stderr;
        this.clock = clock;
    }

    /** Runs the job. Returns 0, or 16 on abend. Writes TRANSACT.txt and ACCTFILE.txt to outDir. */
    public int run(String parm, Path inDir, Path outDir) throws IOException {
        String parmDate = FixedRecords.pad(parm, 10);
        List<String> trans = new ArrayList<>();
        Map<String, String> accts = new TreeMap<>();
        int rc = 0;
        try {
            sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
            List<String> tcat = open(inDir, "TCATBALF", TCAT_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE", false);
            List<String> xrefRows = open(inDir, "XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE", true);
            List<String> discRows = open(inDir, "DISCGRP", DISC_LEN, "ERROR OPENING DALY REJECTS FILE", false);
            List<String> acctRows = open(inDir, "ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE", false);

            Map<String, String> xref = new HashMap<>();
            for (String r : xrefRows) {
                xref.putIfAbsent(r.substring(25, 36), r);
            }
            Map<String, String> disc = new HashMap<>();
            for (String r : discRows) {
                disc.putIfAbsent(r.substring(0, 16), r);
            }
            for (String r : acctRows) {
                accts.put(r.substring(0, 11), r);
            }
            process(parmDate, tcat, xref, disc, accts, trans);
            sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");
        } catch (Abend e) {
            sysout.println("ABENDING PROGRAM");
            stderr.println("ABENDING PROGRAM");
            rc = 16;
        }
        // Abend: transactions written and rewrites done so far stay, as with in-place VSAM.
        Files.createDirectories(outDir);
        FixedRecords.write(outDir.resolve("TRANSACT.txt"), trans);
        FixedRecords.write(outDir.resolve("ACCTFILE.txt"), new ArrayList<>(accts.values()));
        return rc;
    }

    private List<String> open(Path inDir, String dd, int len, String message, boolean statusOnLine)
            throws IOException, Abend {
        Path f = inDir.resolve(dd + ".txt");
        if (!Files.isReadable(f)) {
            sysout.println(statusOnLine ? message + "35" : message);
            sysout.println("FILE STATUS IS: NNNN0035");
            throw new Abend();
        }
        return FixedRecords.read(f, len);
    }

    private void process(String parmDate, List<String> tcat, Map<String, String> xref,
            Map<String, String> disc, Map<String, String> accts, List<String> trans) throws Abend {
        String lastAcct = " ".repeat(11);
        boolean first = true;
        BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
        int suffix = 0;
        String acct = null;
        String card = null;

        for (String rec : tcat) {
            sysout.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(accts, lastAcct, totalInt);
                } else {
                    first = false;
                }
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accts.get(acctId);
                if (acct == null) {
                    notFound(acctId, "ERROR READING ACCOUNT FILE");
                }
                String x = xref.get(acctId);
                if (x == null) {
                    notFound(acctId, "ERROR READING XREF FILE");
                }
                card = x.substring(0, 16);
            }
            String groupId = accts.get(acctId).substring(112, 122);
            String typeCat = rec.substring(11, 17);
            String d = disc.get(groupId + typeCat);
            if (d == null) {
                sysout.println("DISCLOSURE GROUP RECORD MISSING");
                sysout.println("TRY WITH DEFAULT GROUP CODE");
                d = disc.get("DEFAULT   " + typeCat);
                if (d == null) {
                    sysout.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                    sysout.println("FILE STATUS IS: NNNN0023");
                    throw new Abend();
                }
            }
            BigDecimal rate = Zoned.decode(d.substring(16, 22));
            if (rate.signum() == 0) {
                continue;
            }
            BigDecimal bal = Zoned.decode(rec.substring(17, 28));
            // D2: truncated (not rounded); D3: high-order digits silently lost (S9(9)V99).
            BigDecimal monthly = Zoned.truncate(
                    bal.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 11);
            totalInt = Zoned.truncate(totalInt.add(monthly), 11);
            suffix = (suffix + 1) % 1_000_000; // D3: PIC 9(6) wraps
            trans.add(tranRecord(parmDate, suffix, acctId, monthly, card));
            // 1400-COMPUTE-FEES: no-op (D5)
        }
        // D1: the last account is never rewritten (loop exits at EOF before 1050 can run).
    }

    private void notFound(String acctId, String message) throws Abend {
        sysout.println("ACCOUNT NOT FOUND: " + acctId);
        sysout.println(message);
        sysout.println("FILE STATUS IS: NNNN0023");
        throw new Abend();
    }

    private static void updateAccount(Map<String, String> accts, String id, BigDecimal totalInt) {
        String a = accts.get(id);
        BigDecimal bal = Zoned.decode(a.substring(12, 24));
        String newBal = Zoned.encode(bal.add(totalInt), 12);
        String zero = Zoned.encode(BigDecimal.ZERO, 12);
        accts.put(id, a.substring(0, 12) + newBal + a.substring(24, 78) + zero + zero + a.substring(102));
    }

    private String tranRecord(String parmDate, int suffix, String acctId, BigDecimal amt, String card) {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        return parmDate + String.format("%06d", suffix)
                + "01" + "0005"
                + FixedRecords.pad("System", 10)
                + FixedRecords.pad("Int. for a/c " + acctId, 100)
                + Zoned.encode(amt, 11)
                + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card + ts + ts
                + " ".repeat(20);
    }
}
