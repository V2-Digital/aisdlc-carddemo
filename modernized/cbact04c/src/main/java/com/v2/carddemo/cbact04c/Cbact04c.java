package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Java port of CBACT04C (interest calculation). Mirrors the COBOL including its defects;
 * see aisdlc/&lt;run&gt;/spec.md. Layouts: CVTRA01Y, CVACT03Y, CVACT01Y, CVTRA02Y, CVTRA05Y.
 */
public final class Cbact04c {
    static final int TCAT_LEN = 50;
    static final int XREF_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int DISC_LEN = 50;
    static final int TRAN_LEN = 350;

    /** Abend (CEE3ABD 999): the harness maps it to exit status 16. */
    private static final class AbendException extends RuntimeException {
        AbendException() {
            super("ABENDING PROGRAM", null, false, false);
        }
    }

    private final String parm;
    private final Supplier<String> clock;
    private final List<String> sysout = new ArrayList<>();
    private final List<String> transact = new ArrayList<>();
    private TreeMap<String, String> accounts = new TreeMap<>();

    private Cbact04c(String parm, Supplier<String> clock) {
        this.parm = parm;
        this.clock = clock;
    }

    /**
     * Run the job.
     *
     * @param in    path per DD: TCATBALF, XREFFILE, ACCTFILE, DISCGRP
     * @param clock supplies FUNCTION CURRENT-DATE as YYYYMMDDHHMMSSss
     * @return the return code (0, or 16 on abend)
     */
    public static int run(Map<String, Path> in, Path outDir, String parm, Supplier<String> clock)
            throws IOException {
        Cbact04c job = new Cbact04c(parm, clock);
        int rc = 0;
        try {
            job.execute(in);
        } catch (AbendException e) {
            rc = 16;
        }
        Files.createDirectories(outDir);
        Datasets.write(outDir.resolve("TRANSACT.txt"), job.transact);
        Datasets.write(outDir.resolve("ACCTFILE.txt"), job.accounts);
        Datasets.write(outDir.resolve("SYSOUT.txt"), job.sysout);
        Files.writeString(outDir.resolve("RC.txt"), rc + "\n", StandardCharsets.ISO_8859_1);
        return rc;
    }

    private void display(String s) {
        sysout.add(s);
    }

    private void abend() {
        display("ABENDING PROGRAM");
        throw new AbendException();
    }

    private void ioStatus(String status) {
        display("FILE STATUS IS: NNNN00" + status);
    }

    private void openFail(String msg, String status) {
        display(msg);
        ioStatus(status);
        abend();
    }

    private void execute(Map<String, Path> in) throws IOException {
        display("START OF EXECUTION OF PROGRAM CBACT04C");

        List<String> tcatbal = null;
        Path p = in.get("TCATBALF");
        if (p == null || !Files.isReadable(p)) {
            openFail("ERROR OPENING TRANSACTION CATEGORY BALANCE", "35");
        } else {
            tcatbal = new ArrayList<>(Datasets.loadKsds(p, TCAT_LEN, 17).values());
        }

        // alt index: acct-id -> first xref record in file order
        Map<String, String> xrefByAcct = new HashMap<>();
        p = in.get("XREFFILE");
        if (p == null || !Files.isReadable(p)) {
            display("ERROR OPENING CROSS REF FILE35");
            ioStatus("35");
            abend();
        } else {
            for (String rec : Datasets.loadLines(p, XREF_LEN)) {
                xrefByAcct.putIfAbsent(rec.substring(25, 36), rec);
            }
        }

        TreeMap<String, String> discgrp = null;
        p = in.get("DISCGRP");
        if (p == null || !Files.isReadable(p)) {
            openFail("ERROR OPENING DALY REJECTS FILE", "35");
        } else {
            discgrp = Datasets.loadKsds(p, DISC_LEN, 16);
        }

        p = in.get("ACCTFILE");
        if (p == null || !Files.isReadable(p)) {
            openFail("ERROR OPENING ACCOUNT MASTER FILE", "35");
        } else {
            accounts = Datasets.loadKsds(p, ACCT_LEN, 11);
        }

        String lastAcct = "           ";
        boolean first = true;
        BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
        int suffix = 0;
        String account = null;
        String xref = null;

        for (String rec : tcatbal) {
            display(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(account, totalInt);
                } else {
                    first = false;
                }
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                account = accounts.get(acctId);
                if (account == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING ACCOUNT FILE");
                    ioStatus("23");
                    abend();
                }
                xref = xrefByAcct.get(acctId);
                if (xref == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING XREF FILE");
                    ioStatus("23");
                    abend();
                }
            }
            String typeCat = rec.substring(11, 17);
            String disc = discgrp.get(account.substring(112, 122) + typeCat);
            if (disc == null) {
                display("DISCLOSURE GROUP RECORD MISSING");
                display("TRY WITH DEFAULT GROUP CODE");
                disc = discgrp.get("DEFAULT   " + typeCat);
                if (disc == null) {
                    display("ERROR READING DEFAULT DISCLOSURE GROUP");
                    ioStatus("23");
                    abend();
                }
            }
            BigDecimal rate = Overpunch.parse(disc.substring(16, 22));
            if (rate.signum() != 0) {
                BigDecimal bal = Overpunch.parse(rec.substring(17, 28));
                BigDecimal monthly = Overpunch.fit(
                        bal.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 9);
                totalInt = Overpunch.fit(totalInt.add(monthly), 9);
                suffix = (suffix + 1) % 1_000_000;
                writeTransaction(account, xref, monthly, suffix);
            }
            // 1400-COMPUTE-FEES: not implemented in the COBOL either
        }
        // No final updateAccount: the COBOL never reaches it after EOF (defect D1).
        display("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    /** 1050-UPDATE-ACCOUNT: only current balance and cycle credit/debit change. */
    private void updateAccount(String account, BigDecimal totalInt) {
        BigDecimal bal = Overpunch.parse(account.substring(12, 24));
        String newBal = Overpunch.format(bal.add(totalInt), 12);
        String zero = Overpunch.format(BigDecimal.ZERO, 12);
        String rec = account.substring(0, 12) + newBal + account.substring(24, 78)
                + zero + zero + account.substring(102);
        accounts.put(account.substring(0, 11), rec);
    }

    private void writeTransaction(String account, String xref, BigDecimal amt, int suffix) {
        String ts = timestamp();
        StringBuilder sb = new StringBuilder(TRAN_LEN);
        sb.append(pad(parm + String.format("%06d", suffix), 16));
        sb.append("01").append("0005");
        sb.append(pad("System", 10));
        sb.append(pad("Int. for a/c " + account.substring(0, 11), 100));
        sb.append(Overpunch.format(amt, 11));
        sb.append("000000000");
        sb.append(" ".repeat(50 + 50 + 10));
        sb.append(xref.substring(0, 16));
        sb.append(ts).append(ts);
        sb.append(" ".repeat(20));
        transact.add(sb.toString());
    }

    /** Z-GET-DB2-FORMAT-TIMESTAMP: YYYY-MM-DD-HH.MM.SS.hh0000 */
    private String timestamp() {
        String c = clock.get();
        return c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
                + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14) + "."
                + c.substring(14, 16) + "0000";
    }

    private static String pad(String s, int len) {
        return s.length() >= len ? s.substring(0, len) : s + " ".repeat(len - s.length());
    }
}
