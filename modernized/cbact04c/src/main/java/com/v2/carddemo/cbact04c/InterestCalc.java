package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java port of CBACT04C (interest calculation), mirroring app/cbl/CBACT04C.cbl. */
public final class InterestCalc {
    static final int TCAT_LEN = 50, XREF_LEN = 50, ACCT_LEN = 300, DISC_LEN = 50, TRAN_LEN = 350;
    private static final BigDecimal MONTHS_X100 = BigDecimal.valueOf(1200);

    private final Path dataDir;
    private final Path inputDir;
    private final String parm;
    private final Clock clock;

    private final List<String> sysout = new ArrayList<>();
    private final List<String> transact = new ArrayList<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();

    public InterestCalc(Path dataDir, Path inputDir, String parm, Clock clock) {
        this.dataDir = dataDir;
        this.inputDir = inputDir;
        this.parm = RecordFiles.pad(parm, 10);
        this.clock = clock;
    }

    public List<String> sysout() { return sysout; }
    public List<String> transact() { return transact; }
    public Map<String, String> accounts() { return accounts; }

    /** Runs the program; returns the exit code (0 ok, 16 abend). Output files are written only on success. */
    public int run(Path outDir) throws IOException {
        Files.createDirectories(outDir);
        int rc = 0;
        try {
            execute();
            RecordFiles.write(outDir.resolve("TRANSACT.txt"), transact);
            RecordFiles.write(outDir.resolve("ACCTFILE.txt"), accounts.values());
        } catch (AbendException e) {
            rc = 16;
        }
        RecordFiles.write(outDir.resolve("SYSOUT.txt"), sysout);
        Files.writeString(outDir.resolve("RC.txt"), rc + "\n");
        return rc;
    }

    private void display(String s) { sysout.add(s); }

    private AbendException abend(String message, String status) {
        display(message);
        display("FILE STATUS IS: NNNN" + status);
        display("ABENDING PROGRAM");
        return new AbendException();
    }

    private List<String> open(String dd, String sample, int lrecl, String message) throws IOException {
        Path p = RecordFiles.resolve(dataDir, inputDir, dd, sample);
        if (!Files.isRegularFile(p)) {
            throw abend(message, "0035");
        }
        return RecordFiles.load(p, lrecl);
    }

    private void execute() throws IOException {
        display("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcatbal = open("TCATBALF", "tcatbal.txt", TCAT_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xrefRecs = open("XREFFILE", "cardxref.txt", XREF_LEN, "ERROR OPENING CROSS REF FILE35");
        List<String> discRecs = open("DISCGRP", "discgrp.txt", DISC_LEN, "ERROR OPENING DALY REJECTS FILE");
        List<String> acctRecs = open("ACCTFILE", "acctdata.txt", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE");

        tcatbal = new ArrayList<>(tcatbal);
        tcatbal.sort(Comparator.comparing(r -> r.substring(0, 17)));
        TreeMap<String, String> disc = new TreeMap<>();
        for (String r : discRecs) disc.put(r.substring(0, 16), r);
        for (String r : acctRecs) accounts.put(r.substring(0, 11), r);
        // alt key (account id): first card in primary-key (card number) order wins
        TreeMap<String, String> byCard = new TreeMap<>();
        for (String r : xrefRecs) byCard.put(r.substring(0, 16), r);
        Map<String, String> xrefByAcct = new HashMap<>();
        for (String r : byCard.values()) xrefByAcct.putIfAbsent(r.substring(25, 36), r);

        String lastAcct = " ".repeat(11);
        boolean first = true;
        BigDecimal total = BigDecimal.ZERO;
        String acct = null;
        String xref = null;
        int suffix = 0;

        // D1: the COBOL loop never rewrites the last account at EOF; neither do we.
        for (String rec : tcatbal) {
            display(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(acct, total);
                }
                first = false;
                total = BigDecimal.ZERO;
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    throw abend("ERROR READING ACCOUNT FILE", "0023");
                }
                xref = xrefByAcct.get(acctId);
                if (xref == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    throw abend("ERROR READING XREF FILE", "0023");
                }
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            String groupKey = acct.substring(112, 122) + type + cat;
            String d = disc.get(groupKey);
            if (d == null) {
                display("DISCLOSURE GROUP RECORD MISSING");
                display("TRY WITH DEFAULT GROUP CODE");
                d = disc.get(RecordFiles.pad("DEFAULT", 10) + type + cat);
                if (d == null) {
                    throw abend("ERROR READING DEFAULT DISCLOSURE GROUP", "0023");
                }
            }
            BigDecimal rate = Zoned.decode(d.substring(16, 22), 2);
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28), 2);
                BigDecimal monthly = Zoned.truncate(
                        bal.multiply(rate).divide(MONTHS_X100, 2, RoundingMode.DOWN), 11, 2);
                total = Zoned.truncate(total.add(monthly), 11, 2);
                suffix = (suffix + 1) % 1_000_000;
                transact.add(transaction(suffix, acctId, monthly, xref.substring(0, 16)));
                // 1400-COMPUTE-FEES is an empty paragraph (D4)
            }
        }
        display("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private void updateAccount(String acct, BigDecimal total) {
        BigDecimal bal = Zoned.decode(acct.substring(12, 24), 2);
        String zero = Zoned.encode(BigDecimal.ZERO, 12, 2);
        String updated = acct.substring(0, 12)
                + Zoned.encode(bal.add(total), 12, 2)
                + acct.substring(24, 78) + zero + zero
                + acct.substring(102);
        accounts.put(acct.substring(0, 11), updated);
    }

    private String transaction(int suffix, String acctId, BigDecimal amount, String card) {
        String ts = timestamp();
        return parm + String.format("%06d", suffix)
                + "01" + "0005"
                + RecordFiles.pad("System", 10)
                + RecordFiles.pad("Int. for a/c " + acctId, 100)
                + Zoned.encode(amount, 11, 2)
                + "000000000"
                + " ".repeat(110)
                + card
                + ts + ts
                + " ".repeat(20);
    }

    /** DB2-format timestamp YYYY-MM-DD-HH.MM.SS.hh0000 from the injected clock. */
    private String timestamp() {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }
}
