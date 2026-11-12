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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Java rewrite of COBOL program CBACT04C (interest calculation), behaviour-for-behaviour. */
public final class InterestCalc {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int DISCGRP_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int TRAN_LEN = 350;

    // ACCOUNT-RECORD offsets (CVACT01Y)
    private static final int ACCT_BAL = 12;
    private static final int ACCT_CYC_CREDIT = 78;
    private static final int ACCT_CYC_DEBIT = 90;
    private static final int ACCT_GROUP = 112;

    private final Map<String, Path> dd;
    private final String parmDate;
    private final Clock clock;
    private final PrintStream sysout;
    private final PrintStream stderr;

    private String lastAcct = "";
    private boolean firstTime = true;
    private BigDecimal totalInt = BigDecimal.ZERO;
    private int tranIdSuffix = 0;

    private final Map<String, String> accounts = new LinkedHashMap<>();
    private final List<String[]> xrefs = new ArrayList<>();
    private final Map<String, String> discgrp = new LinkedHashMap<>();
    private final List<String> transactions = new ArrayList<>();
    private String acct;
    private String xrefCard;

    public InterestCalc(Map<String, Path> dd, String parm, Clock clock, PrintStream sysout, PrintStream stderr) {
        this.dd = dd;
        this.parmDate = FixedWidth.pad(parm, 10);
        this.clock = clock;
        this.sysout = sysout;
        this.stderr = stderr;
    }

    /** Runs the job; returns the return code. */
    public int run() throws IOException {
        try {
            execute();
            return 0;
        } catch (AbendException e) {
            stderr.println("CEE3ABD: ABEND U0999");
            return AbendException.RC;
        }
    }

    private void execute() throws IOException {
        sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcatbal = open("TCATBALF", TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        for (String r : open("XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE")) {
            xrefs.add(new String[] {r.substring(25, 36), r.substring(0, 16)});
        }
        for (String r : open("DISCGRP", DISCGRP_LEN, "ERROR OPENING DALY REJECTS FILE")) {
            discgrp.putIfAbsent(r.substring(0, 16), r);
        }
        for (String r : open("ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE")) {
            accounts.put(r.substring(0, 11), r);
        }
        if (!Files.isWritable(dd.get("TRANSACT").toAbsolutePath().getParent())) {
            sysout.println("ERROR OPENING TRANSACTION FILE");
            ioStatus("37");
            abend();
        }

        for (String rec : tcatbal) {
            sysout.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!firstTime) {
                    updateAccount();
                } else {
                    firstTime = false;
                }
                totalInt = BigDecimal.ZERO;
                lastAcct = acctId;
                getAccount(acctId);
                getXref(acctId);
            }
            BigDecimal rate = ZonedDecimal.decode(interestRateRecord(rec).substring(16, 22), 2);
            if (rate.signum() != 0) {
                BigDecimal bal = ZonedDecimal.decode(rec.substring(17, 28), 2);
                computeInterest(rec, bal, rate);
            }
        }
        // F1: the loop ends at EOF before the last account is rewritten.

        FixedWidth.write(dd.get("TRANSACT"), transactions);
        FixedWidth.write(dd.get("ACCTFILE"), new ArrayList<>(accounts.values()));
        sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private List<String> open(String name, int lrecl, String message) {
        try {
            return FixedWidth.read(dd.get(name), lrecl);
        } catch (IOException | RuntimeException e) {
            sysout.println(message);
            ioStatus("35");
            abend();
            return List.of();
        }
    }

    private void getAccount(String id) {
        acct = accounts.get(id);
        if (acct == null) {
            sysout.println("ACCOUNT NOT FOUND: " + id);
            sysout.println("ERROR READING ACCOUNT FILE");
            ioStatus("23");
            abend();
        }
    }

    private void getXref(String id) {
        for (String[] x : xrefs) {
            if (x[0].equals(id)) {
                xrefCard = x[1];
                return;
            }
        }
        sysout.println("ACCOUNT NOT FOUND: " + id);
        sysout.println("ERROR READING XREF FILE");
        ioStatus("23");
        abend();
    }

    private String interestRateRecord(String tcatRec) {
        String suffix = tcatRec.substring(11, 17);
        String hit = discgrp.get(FixedWidth.pad(acct.substring(ACCT_GROUP, ACCT_GROUP + 10), 10) + suffix);
        if (hit != null) {
            return hit;
        }
        sysout.println("DISCLOSURE GROUP RECORD MISSING");
        sysout.println("TRY WITH DEFAULT GROUP CODE");
        hit = discgrp.get("DEFAULT   " + suffix);
        if (hit == null) {
            sysout.println("ERROR READING DEFAULT DISCLOSURE GROUP");
            ioStatus("23");
            abend();
        }
        return hit;
    }

    private void computeInterest(String tcatRec, BigDecimal bal, BigDecimal rate) {
        // F2: COMPUTE without ROUNDED truncates toward zero; S9(9)V99 drops high-order digits.
        BigDecimal exact = bal.multiply(rate).divide(BigDecimal.valueOf(1200), 10, RoundingMode.DOWN);
        BigDecimal monthly = ZonedDecimal.wrap(exact.setScale(2, RoundingMode.DOWN), 11, 2);
        totalInt = ZonedDecimal.wrap(totalInt.add(monthly), 11, 2);
        writeTransaction(monthly);
        // 1400-COMPUTE-FEES: not implemented in the legacy program (F3).
    }

    private void writeTransaction(BigDecimal amount) {
        tranIdSuffix = (tranIdSuffix + 1) % 1_000_000; // F4
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", now.getYear(), now.getMonthValue(),
                now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano() / 10_000_000);
        StringBuilder sb = new StringBuilder();
        sb.append(parmDate).append(String.format("%06d", tranIdSuffix)); // TRAN-ID 16
        sb.append("01").append("0005");
        sb.append(FixedWidth.pad("System", 10));
        sb.append(FixedWidth.pad("Int. for a/c " + acct.substring(0, 11), 100));
        sb.append(ZonedDecimal.encode(amount, 11, 2));
        sb.append("000000000"); // merchant id
        sb.append(" ".repeat(110)); // merchant name, city, zip
        sb.append(xrefCard);
        sb.append(ts).append(ts);
        transactions.add(FixedWidth.pad(sb.toString(), TRAN_LEN));
    }

    private void updateAccount() {
        BigDecimal bal = ZonedDecimal.decode(acct.substring(ACCT_BAL, ACCT_BAL + 12), 2);
        String rec = FixedWidth.overlay(acct, ACCT_BAL, ZonedDecimal.encode(bal.add(totalInt), 12, 2));
        String zero = ZonedDecimal.encode(BigDecimal.ZERO, 12, 2);
        rec = FixedWidth.overlay(rec, ACCT_CYC_CREDIT, zero);
        rec = FixedWidth.overlay(rec, ACCT_CYC_DEBIT, zero);
        acct = rec;
        accounts.put(rec.substring(0, 11), rec);
    }

    private void ioStatus(String status) {
        sysout.println("FILE STATUS IS: NNNN00" + status);
    }

    private void abend() {
        sysout.println("ABENDING PROGRAM");
        throw new AbendException();
    }
}
