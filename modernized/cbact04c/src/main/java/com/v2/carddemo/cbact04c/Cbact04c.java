package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Java rewrite of CBACT04C (interest calculation). Behaviour-preserving, defects included. */
public final class Cbact04c {
    private static final BigDecimal ZERO2 = new BigDecimal("0.00");
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private final Path tcatbalf;
    private final Path xreffile;
    private final Path discgrp;
    private final Path acctfile;
    private final Path outDir;
    private final Supplier<String> clock;
    private final PrintStream sysout;

    private String lastAcct = "";
    private boolean firstTime = true;
    private BigDecimal totalInt = ZERO2;
    private int suffix = 0;
    private String acctKey;
    private String card;

    public Cbact04c(Path tcatbalf, Path xreffile, Path discgrp, Path acctfile, Path outDir,
                    Supplier<String> clock, PrintStream sysout) {
        this.tcatbalf = tcatbalf;
        this.xreffile = xreffile;
        this.discgrp = discgrp;
        this.acctfile = acctfile;
        this.outDir = outDir;
        this.clock = clock;
        this.sysout = sysout;
    }

    /** Runs the job; returns the return code (0, or 16 on abend). */
    public int run(String parm) throws IOException {
        String parmDate = RecordIo.pad(parm, 10).substring(0, 10);
        sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            return execute(parmDate);
        } catch (AbendException e) {
            sysout.println("ABENDING PROGRAM");
            return AbendException.EXIT_CODE;
        }
    }

    private int execute(String parmDate) throws IOException {
        List<String> cats = open(tcatbalf, 50, "TRANSACTION CATEGORY BALANCE", "TCATBALF");
        List<String> xrefs = open(xreffile, 50, "XREF", "XREFFILE");
        List<String> rates = open(discgrp, 50, "DALY REJECTS", "DISCGRP");
        List<String> accts = open(acctfile, 300, "ACCOUNT", "ACCTFILE");

        Map<String, String> xrefByAcct = new HashMap<>();
        for (String x : xrefs) {
            xrefByAcct.putIfAbsent(x.substring(25, 36), x);
        }
        Map<String, BigDecimal> rateByKey = new HashMap<>();
        for (String r : rates) {
            rateByKey.put(r.substring(0, 16), parseNum(() -> Zoned.decode(r.substring(16, 22), 2), "DISCGRP"));
        }
        TreeMap<String, String> accounts = new TreeMap<>();
        for (String a : accts) {
            accounts.put(a.substring(0, 11), a);
        }

        Files.createDirectories(outDir);
        try (OutputStream tran = Files.newOutputStream(outDir.resolve("TRANSACT.txt"))) {
            for (String rec : cats) {
                sysout.println(rec);
                String acct = rec.substring(0, 11);
                String type = rec.substring(11, 13);
                String cat = rec.substring(13, 17);
                if (!acct.equals(lastAcct)) {
                    if (!firstTime) {
                        updateAccount(accounts);
                    } else {
                        firstTime = false;
                    }
                    totalInt = ZERO2;
                    lastAcct = acct;
                    acctKey = acct;
                    if (!accounts.containsKey(acct)) {
                        fail("ACCOUNT NOT FOUND: " + acct, "ERROR READING ACCOUNT FILE");
                    }
                    String x = xrefByAcct.get(acct);
                    if (x == null) {
                        fail("ACCOUNT NOT FOUND: " + acct, "ERROR READING XREF FILE");
                    }
                    card = x.substring(0, 16);
                }
                String group = accounts.get(acctKey).substring(112, 122);
                BigDecimal rate = rateByKey.get(group + type + cat);
                if (rate == null) {
                    sysout.println("DISCLOSURE GROUP RECORD MISSING");
                    sysout.println("TRY WITH DEFAULT GROUP CODE");
                    rate = rateByKey.get("DEFAULT   " + type + cat);
                    if (rate == null) {
                        fail("ERROR READING DEFAULT DISCLOSURE GROUP", null);
                    }
                }
                if (rate.signum() != 0) {
                    BigDecimal bal = parseNum(() -> Zoned.decode(rec.substring(17, 28), 2), "TCATBALF");
                    BigDecimal interest = Zoned.truncate(
                            bal.multiply(rate).divide(TWELVE_HUNDRED, 10, RoundingMode.DOWN), 9, 2);
                    totalInt = Zoned.truncate(totalInt.add(interest), 9, 2);
                    writeTransaction(tran, parmDate, acctKey, interest);
                }
            }
            // D1: the last account is never rewritten (the COBOL ELSE branch is dead code).
        }
        sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");
        try (OutputStream out = Files.newOutputStream(outDir.resolve("ACCTFILE.txt"))) {
            for (String a : accounts.values()) {
                RecordIo.write(out, a);
            }
        }
        return 0;
    }

    private void updateAccount(Map<String, String> accounts) {
        String a = accounts.get(lastAcct);
        BigDecimal bal = parseNum(() -> Zoned.decode(a.substring(12, 24), 2), "ACCTFILE");
        bal = Zoned.truncate(bal.add(totalInt), 10, 2);
        String zero = Zoned.encode(ZERO2, 12, 2);
        accounts.put(lastAcct, a.substring(0, 12) + Zoned.encode(bal, 12, 2) + a.substring(24, 78)
                + zero + zero + a.substring(102));
    }

    private void writeTransaction(OutputStream out, String parmDate, String acct, BigDecimal amt) throws IOException {
        suffix = (suffix + 1) % 1_000_000;
        String ts = clock.get();
        String desc = RecordIo.pad("Int. for a/c " + acct, 100);
        String rec = parmDate + String.format("%06d", suffix) + "01" + "0005"
                + RecordIo.pad("System", 10) + desc + Zoned.encode(amt, 11, 2)
                + "000000000" + " ".repeat(110) + card + ts + ts + " ".repeat(20);
        RecordIo.write(out, rec);
    }

    private List<String> open(Path file, int lrecl, String what, String dd) throws IOException {
        if (!Files.isReadable(file)) {
            sysout.println("ERROR OPENING " + what + " FILE");
            sysout.println("FILE STATUS IS: NNNN0035");
            throw new AbendException("open " + dd);
        }
        return RecordIo.read(file, lrecl);
    }

    private BigDecimal parseNum(java.util.function.Supplier<BigDecimal> s, String dd) {
        try {
            return s.get();
        } catch (NumberFormatException e) {
            sysout.println("ERROR READING " + dd);
            throw new AbendException("bad numeric in " + dd);
        }
    }

    private void fail(String first, String second) {
        sysout.println(first);
        if (second != null) {
            sysout.println(second);
        }
        sysout.println("FILE STATUS IS: NNNN0023");
        throw new AbendException(first);
    }
}
