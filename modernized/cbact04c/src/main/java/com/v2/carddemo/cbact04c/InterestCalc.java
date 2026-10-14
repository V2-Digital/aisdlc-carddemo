package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Records.cols;
import static com.v2.carddemo.cbact04c.Records.pad;
import static com.v2.carddemo.cbact04c.Records.patch;

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

/**
 * Java port of CBACT04C (interest calculation). Legacy behaviour is kept as-is,
 * defects included (see README).
 */
public final class InterestCalc {
    private static final BigDecimal MONTHS_PCT = BigDecimal.valueOf(1200);

    private final String parm;
    private final String now; // YYYYMMDDHHMMSSss
    private final List<String> sysout = new ArrayList<>();
    private final List<String> transact = new ArrayList<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();

    public InterestCalc(String parm, String now) {
        this.parm = pad(parm, 10);
        this.now = now;
    }

    /** Runs the job; returns the return code (0, or 16 on abend). */
    public int run(Path in, Path out) throws IOException {
        int rc = 0;
        try {
            process(in);
        } catch (AbendException e) {
            System.err.println(e.getMessage());
            rc = e.code;
        } catch (IllegalArgumentException e) {
            sysout.add("ABENDING PROGRAM");
            System.err.println(e.getMessage());
            rc = 16;
        }
        Files.createDirectories(out);
        Records.write(out.resolve("TRANSACT.txt"), transact);
        if (!accounts.isEmpty()) {
            Records.write(out.resolve("ACCTFILE.txt"), new ArrayList<>(accounts.values()));
        }
        Records.write(out.resolve("SYSOUT.txt"), sysout);
        return rc;
    }

    public List<String> sysout() {
        return sysout;
    }

    private void process(Path in) throws IOException {
        sysout.add("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcatbal = open(in, "TCATBALF", 50, "ERROR OPENING TRANSACTION CATEGORY BALANCE", false);
        List<String> xrefRecs = open(in, "XREFFILE", 50, "ERROR OPENING CROSS REF FILE", true);
        List<String> discRecs = open(in, "DISCGRP", 50, "ERROR OPENING DALY REJECTS FILE", false);
        List<String> acctRecs = open(in, "ACCTFILE", 300, "ERROR OPENING ACCOUNT MASTER FILE", false);

        Map<String, String> xref = new HashMap<>(); // acct id -> first card in file order
        for (String r : xrefRecs) {
            xref.putIfAbsent(cols(r, 26, 36), r);
        }
        Map<String, String> disc = new HashMap<>();
        for (String r : discRecs) {
            disc.put(cols(r, 1, 16), r);
        }
        for (String r : acctRecs) {
            accounts.put(cols(r, 1, 11), r);
        }

        String lastAcct = " ".repeat(11);
        boolean first = true;
        BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
        int suffix = 0;
        String acct = null;
        String card = null;

        for (String rec : tcatbal) {
            sysout.add(rec);
            String acctId = cols(rec, 1, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(lastAcct, acct, totalInt);
                }
                first = false;
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    sysout.add("ACCOUNT NOT FOUND: " + acctId);
                    sysout.add("ERROR READING ACCOUNT FILE");
                    abend("0023");
                }
                String x = xref.get(acctId);
                if (x == null) {
                    sysout.add("ACCOUNT NOT FOUND: " + acctId);
                    sysout.add("ERROR READING XREF FILE");
                    abend("0023");
                }
                card = cols(x, 1, 16);
            }
            String type = cols(rec, 12, 13);
            String cat = cols(rec, 14, 17);
            String rateRec = disc.get(cols(acct, 113, 122) + type + cat);
            if (rateRec == null) {
                sysout.add("DISCLOSURE GROUP RECORD MISSING");
                sysout.add("TRY WITH DEFAULT GROUP CODE");
                rateRec = disc.get("DEFAULT   " + type + cat);
                if (rateRec == null) {
                    sysout.add("ERROR READING DEFAULT DISCLOSURE GROUP");
                    abend("0023");
                }
            }
            BigDecimal rate = Zoned.decode(cols(rateRec, 17, 22), 2);
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(cols(rec, 18, 28), 2);
                BigDecimal m = bal.multiply(rate).divide(MONTHS_PCT, 2, RoundingMode.DOWN);
                m = Zoned.truncInt(m, 9);
                totalInt = Zoned.truncInt(totalInt.add(m), 9);
                suffix = (suffix + 1) % 1_000_000;
                transact.add(txRecord(suffix, acctId, m, card));
            }
        }
        // D1: no update for the last account (COBOL's ELSE branch is unreachable).
        sysout.add("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private void updateAccount(String id, String rec, BigDecimal totalInt) {
        BigDecimal bal = Zoned.decode(cols(rec, 13, 24), 2);
        bal = Zoned.truncInt(bal.add(totalInt), 10);
        String zero = Zoned.encode(BigDecimal.ZERO.setScale(2), 12);
        rec = patch(rec, 13, Zoned.encode(bal, 12));
        rec = patch(rec, 79, zero + zero);
        accounts.put(id, rec);
    }

    private String txRecord(int suffix, String acctId, BigDecimal amt, String card) {
        String ts = timestamp();
        return String.format("%s%06d", parm, suffix)
                + "01" + "0005"
                + pad("System", 10)
                + pad("Int. for a/c " + acctId, 100)
                + Zoned.encode(amt, 11)
                + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card
                + ts + ts
                + " ".repeat(20);
    }

    /** YYYY-MM-DD-HH.MM.SS.hh0000 from YYYYMMDDHHMMSSss. */
    private String timestamp() {
        return now.substring(0, 4) + "-" + now.substring(4, 6) + "-" + now.substring(6, 8) + "-"
                + now.substring(8, 10) + "." + now.substring(10, 12) + "." + now.substring(12, 14)
                + "." + now.substring(14, 16) + "0000";
    }

    private List<String> open(Path in, String dd, int lrecl, String msg, boolean statusInMsg) throws IOException {
        Path f = in.resolve(dd + ".txt");
        if (!Files.isReadable(f)) {
            sysout.add(msg + (statusInMsg ? "35" : ""));
            abend("0035");
        }
        return Records.read(f, lrecl);
    }

    private void abend(String status) {
        sysout.add("FILE STATUS IS: NNNN" + status);
        sysout.add("ABENDING PROGRAM");
        throw new AbendException(16);
    }
}
