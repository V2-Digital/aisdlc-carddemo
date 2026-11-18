package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java rewrite of CBACT04C (interest calculation). Behaviour-preserving, defects D1-D9 kept. */
public final class Cbact04c {
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    /** COBOL abend (CEE3ABD U0999): RC 16. */
    static final class Abend extends RuntimeException {
        Abend() {
            super("ABEND U0999", null, false, false);
        }
    }

    private final String parm;
    private final String clock;
    private final List<String> sysout = new ArrayList<>();
    private final List<String> transact = new ArrayList<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();
    private int rc;

    /** @param clock YYYYMMDDHHMMSSss (16 digits) */
    public Cbact04c(String parm, String clock) {
        this.parm = Records.pad(parm, 10);
        this.clock = clock;
    }

    public List<String> sysout() { return sysout; }
    public List<String> transact() { return transact; }
    public List<String> accounts() { return new ArrayList<>(accounts.values()); }
    public int rc() { return rc; }

    /** Runs the job against {@code in}; outputs are collected in memory. Returns RC. */
    public int run(Path in) throws IOException {
        sysout.add("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            execute(in);
            rc = 0;
        } catch (Abend a) {
            rc = 16;
        }
        return rc;
    }

    /** Writes TRANSACT.txt, ACCTFILE.txt, SYSOUT.txt and RC.txt to {@code out}. */
    public void writeOutputs(Path out) throws IOException {
        Files.createDirectories(out);
        Records.write(out.resolve("TRANSACT.txt"), transact);
        Records.write(out.resolve("ACCTFILE.txt"), accounts());
        Records.write(out.resolve("SYSOUT.txt"), sysout);
        Files.writeString(out.resolve("RC.txt"), rc + "\n");
    }

    private void execute(Path in) throws IOException {
        List<String> tcatbal = open(in, "TCATBALF", Records.TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        Map<String, String> xref = new HashMap<>();
        for (String r : open(in, "XREFFILE", Records.XREF_LEN, "ERROR OPENING CROSS REF FILE")) {
            xref.putIfAbsent(r.substring(25, 36), r); // D9: first per acct id, primary-key order
        }
        Map<String, String> disc = new HashMap<>();
        for (String r : open(in, "DISCGRP", Records.DISCGRP_LEN, "ERROR OPENING DALY REJECTS FILE")) { // D5
            disc.put(r.substring(0, 16), r);
        }
        for (String r : open(in, "ACCTFILE", Records.ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE")) {
            accounts.put(r.substring(0, 11), r);
        }

        String lastAcct = "";
        boolean first = true;
        BigDecimal totalInt = BigDecimal.ZERO;
        String acct = null;
        String card = null;
        int suffix = 0;
        List<String> sorted = new ArrayList<>(tcatbal);
        sorted.sort((a, b) -> a.substring(0, 17).compareTo(b.substring(0, 17)));

        for (String rec : sorted) {
            sysout.add(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(lastAcct, totalInt);
                } else {
                    first = false;
                }
                totalInt = BigDecimal.ZERO;
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    sysout.add("ACCOUNT NOT FOUND: " + acctId);
                    abend("ERROR READING ACCOUNT FILE", "0023");
                }
                String x = xref.get(acctId);
                if (x == null) {
                    sysout.add("ACCOUNT NOT FOUND: " + acctId);
                    abend("ERROR READING XREF FILE", "0023");
                }
                card = x.substring(0, 16);
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            String d = disc.get(acct.substring(112, 122) + type + cat);
            if (d == null) {
                sysout.add("DISCLOSURE GROUP RECORD MISSING");
                sysout.add("TRY WITH DEFAULT GROUP CODE");
                d = disc.get(Records.pad("DEFAULT", 10) + type + cat);
                if (d == null) {
                    abend("ERROR READING DEFAULT DISCLOSURE GROUP", "0023");
                }
            }
            BigDecimal rate = Records.rate(d);
            if (rate.signum() != 0) {
                BigDecimal monthly = Zoned.truncate(
                        Records.catBal(rec).multiply(rate).divide(TWELVE_HUNDRED, 10, RoundingMode.DOWN), 9, 2); // D3
                totalInt = Zoned.truncate(totalInt.add(monthly), 9, 2);
                suffix = (suffix + 1) % 1_000_000; // D7
                transact.add(transaction(suffix, acctId, monthly, card));
                // 1400-COMPUTE-FEES: empty paragraph (D4)
            }
        }
        // D1: last account is never rewritten.
        sysout.add("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private List<String> open(Path in, String dd, int lrecl, String message) throws IOException {
        Path f = in.resolve(dd + ".txt");
        if (!Files.isRegularFile(f)) {
            abend(message, "0035");
        }
        return Records.read(f, lrecl);
    }

    private void updateAccount(String id, BigDecimal totalInt) {
        String rec = accounts.get(id);
        BigDecimal bal = Zoned.truncate(Records.acctBal(rec).add(totalInt), 10, 2);
        accounts.put(id, Records.rewriteAccount(rec, bal)); // D6: even when totalInt is 0
    }

    private String transaction(int suffix, String acctId, BigDecimal amount, String card) {
        String ts = timestamp();
        return parm + String.format("%06d", suffix)
                + "01" + "0005"
                + Records.pad("System", 10)
                + Records.pad("Int. for a/c " + acctId, 100) // D8
                + Zoned.encode(amount, 11, 2)
                + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card
                + ts + ts
                + " ".repeat(20);
    }

    /** YYYY-MM-DD-HH.MM.SS.hh0000 from the 16-digit clock. */
    private String timestamp() {
        String c = clock;
        return c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
                + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14) + "."
                + c.substring(14, 16) + "0000";
    }

    private void abend(String message, String status) {
        sysout.add(message);
        sysout.add("FILE STATUS IS: NNNN" + status);
        sysout.add("ABENDING PROGRAM");
        throw new Abend();
    }
}
