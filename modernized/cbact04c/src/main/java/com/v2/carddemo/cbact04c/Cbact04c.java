package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
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
 * Like-for-like Java port of CBACT04C (INTCALC). Legacy defects are preserved on purpose:
 * the last account is never rewritten, fees are a no-op, interest is truncated, no size errors.
 */
public final class Cbact04c {
    /** Abend U0999: exit code 16. */
    static final class Abend extends RuntimeException {
        Abend() { super("ABEND U0999", null, false, false); }
    }

    private final Path inDir;
    private final Path outDir;
    private final String parm;
    private final Clock clock;
    private final PrintStream sysout;
    private final PrintStream syserr;

    public Cbact04c(Path inDir, Path outDir, String parm, Clock clock, PrintStream sysout, PrintStream syserr) {
        this.inDir = inDir;
        this.outDir = outDir;
        this.parm = Datasets.pad(parm, 10);
        this.clock = clock;
        this.sysout = sysout;
        this.syserr = syserr;
    }

    private final List<String> transact = new ArrayList<>();
    private TreeMap<String, String> accounts;

    /** Runs the job, writes TRANSACT.txt and ACCTFILE.txt to the output dir, returns the exit code. */
    public int run() throws IOException {
        int rc = 0;
        try {
            execute();
        } catch (Abend a) {
            syserr.println("CEE3ABD: ABEND U0999");
            rc = 16;
        }
        // TRANSACT is sequential and ACCTFILE REWRITE is immediate, so partial results stay on abend.
        Datasets.write(outDir.resolve("TRANSACT.txt"), transact);
        if (accounts != null) Datasets.writeMap(outDir.resolve("ACCTFILE.txt"), accounts);
        return rc;
    }

    private void execute() throws IOException {
        sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcatbal = Datasets.read(inDir.resolve("TCATBALF.txt"), 50);
        if (tcatbal == null) openFailed("ERROR OPENING TRANSACTION CATEGORY BALANCE");
        Map<String, String> xrefFirst = new HashMap<>();
        List<String> xref = Datasets.read(inDir.resolve("XREFFILE.txt"), 50);
        if (xref == null) openFailed("ERROR OPENING CROSS REF FILE");
        for (String r : xref) xrefFirst.putIfAbsent(r.substring(25, 36), r); // alt key: first match
        Map<String, String> discgrp = Datasets.readKeyed(inDir.resolve("DISCGRP.txt"), 50, 0, 16);
        if (discgrp == null) openFailed("ERROR OPENING DALY REJECTS FILE"); // D4: wrong text, preserved
        accounts = Datasets.readKeyed(inDir.resolve("ACCTFILE.txt"), 300, 0, 11);
        if (accounts == null) openFailed("ERROR OPENING ACCOUNT MASTER FILE");

        String lastAcct = "           ";
        boolean first = true;
        long totalInt = 0;
        int tranIdSuffix = 0;
        String acct = null;
        String card = null;

        for (String rec : tcatbal) {
            sysout.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) updateAccount(acct, totalInt);
                first = false;
                totalInt = 0;
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    sysout.println("ACCOUNT NOT FOUND: " + acctId);
                    ioError("ERROR READING ACCOUNT FILE", "23");
                }
                String x = xrefFirst.get(acctId);
                if (x == null) {
                    sysout.println("ACCOUNT NOT FOUND: " + acctId);
                    ioError("ERROR READING XREF FILE", "23");
                }
                card = x.substring(0, 16);
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            long bal = Zoned.decode(rec.substring(17, 28));
            String group = acct.substring(112, 122);

            String rateRec = discgrp.get(group + type + cat);
            if (rateRec == null) {
                sysout.println("DISCLOSURE GROUP RECORD MISSING");
                sysout.println("TRY WITH DEFAULT GROUP CODE");
                rateRec = discgrp.get("DEFAULT   " + type + cat);
                if (rateRec == null) ioError("ERROR READING DEFAULT DISCLOSURE GROUP", "23");
            }
            long rate = Zoned.decode(rateRec.substring(16, 22));
            if (rate != 0) {
                long monthly = Zoned.truncate(bal * rate / 120000, 11); // no ROUNDED: truncate toward zero
                totalInt = Zoned.truncate(totalInt + monthly, 11);
                tranIdSuffix = (tranIdSuffix + 1) % 1_000_000;
                transact.add(tranRecord(tranIdSuffix, acctId, monthly, card));
                // 1400-COMPUTE-FEES: empty in legacy (D2)
            }
        }
        // D1: the last account's interest is never posted (loop exits before the unreachable ELSE branch).
        sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private void updateAccount(String rec, long totalInt) {
        long bal = Zoned.decode(rec.substring(12, 24));
        bal = Zoned.truncate(bal + totalInt, 12);
        StringBuilder sb = new StringBuilder(rec);
        sb.replace(12, 24, Zoned.encode(bal, 12));
        sb.replace(78, 90, Zoned.encode(0, 12)); // ACCT-CURR-CYC-CREDIT
        sb.replace(90, 102, Zoned.encode(0, 12)); // ACCT-CURR-CYC-DEBIT
        accounts.put(rec.substring(0, 11), sb.toString());
    }

    private String tranRecord(int suffix, String acctId, long amt, String card) {
        String ts = timestamp();
        StringBuilder sb = new StringBuilder(350);
        sb.append(parm).append(Zoned.digits(suffix, 6));
        sb.append("01").append("0005").append(Datasets.pad("System", 10));
        sb.append(Datasets.pad("Int. for a/c " + acctId, 100));
        sb.append(Zoned.encode(amt, 11));
        sb.append("000000000");
        sb.append(" ".repeat(110));
        sb.append(card).append(ts).append(ts);
        sb.append(" ".repeat(20));
        return sb.toString();
    }

    /** DB2 format timestamp YYYY-MM-DD-HH.MM.SS.hh0000, from the injected clock (UTC). */
    private String timestamp() {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    private void openFailed(String text) {
        sysout.println(text);
        ioStatus("35");
        abend();
    }

    private void ioError(String text, String status) {
        sysout.println(text);
        ioStatus(status);
        abend();
    }

    private void ioStatus(String status) {
        sysout.println("FILE STATUS IS: NNNN00" + status);
    }

    private void abend() {
        sysout.println("ABENDING PROGRAM");
        throw new Abend();
    }
}
