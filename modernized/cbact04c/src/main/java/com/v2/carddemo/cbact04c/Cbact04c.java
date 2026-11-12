package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.FixedRecord.get;
import static com.v2.carddemo.cbact04c.FixedRecord.pad;
import static com.v2.carddemo.cbact04c.FixedRecord.put;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Java port of CBACT04C (interest calculation). Like-for-like, including legacy defects
 * (see modernized/cbact04c/README.md).
 */
public final class Cbact04c {
    static final int TCAT_LEN = 50;
    static final int XREF_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int DISC_LEN = 50;
    static final int TRAN_LEN = 350;

    /** COBOL abend (CEE3ABD U0999). */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    private final Clock clock;
    private final String parmDate;
    private final StringBuilder sysout = new StringBuilder();
    private final List<String> transact = new ArrayList<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();
    private final Map<String, String> xrefByAcct = new HashMap<>();
    private final Map<String, String> discgrp = new HashMap<>();

    private int tranIdSuffix;
    private long totalInt;
    private boolean totalNegZero;
    private String acct;      // current ACCOUNT-RECORD
    private String xref;      // current CARD-XREF-RECORD
    private String rateRec;   // current DIS-GROUP-RECORD

    private Cbact04c(String parm, Clock clock) {
        this.parmDate = pad(parm == null ? "" : parm, 10);
        this.clock = clock;
    }

    /** Runs the job; returns the return code (0, or 16 on abend). Writes TRANSACT, ACCTFILE, SYSOUT to outDir. */
    public static int run(String parm, Clock clock, Path inDir, Path outDir, java.io.PrintStream err)
            throws IOException {
        Files.createDirectories(outDir);
        Cbact04c job = new Cbact04c(parm, clock);
        int rc = 0;
        try {
            job.execute(inDir);
        } catch (Abend a) {
            err.println("CEE3ABD: ABEND U0999");
            rc = 16;
        }
        FixedRecord.writeAll(outDir.resolve("TRANSACT.txt"), job.transact, TRAN_LEN);
        {
            FixedRecord.writeAll(outDir.resolve("ACCTFILE.txt"), new ArrayList<>(job.accounts.values()), ACCT_LEN);
        }
        Files.writeString(outDir.resolve("SYSOUT.txt"), job.sysout.toString(),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        return rc;
    }

    private void display(String s) {
        sysout.append(s).append('\n');
    }

    private void execute(Path in) throws IOException {
        display("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcat = open(in, "TCATBALF", TCAT_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        for (String r : open(in, "XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE")) {
            xrefByAcct.putIfAbsent(get(r, 26, 11), r);
        }
        for (String r : open(in, "DISCGRP", DISC_LEN, "ERROR OPENING DALY REJECTS FILE")) {
            discgrp.putIfAbsent(get(r, 1, 16), r);
        }
        for (String r : open(in, "ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE")) {
            accounts.put(get(r, 1, 11), r);
        }

        String lastAcct = "           ";
        boolean first = true;
        for (String t : tcat) {
            display(t);
            String acctId = get(t, 1, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount();
                } else {
                    first = false;
                }
                totalInt = 0;
                totalNegZero = false;
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    errorAbend("ERROR READING ACCOUNT FILE", "23");
                }
                xref = xrefByAcct.get(acctId);
                if (xref == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    errorAbend("ERROR READING XREF FILE", "23");
                }
            }
            getInterestRate(get(acct, 113, 10), get(t, 12, 2), get(t, 14, 4));
            long rate = ZonedDecimal.decode(get(rateRec, 17, 6));
            if (rate != 0) {
                computeInterest(t, rate);
            }
        }
        // D1: the last account is never rewritten (COBOL's ELSE branch is unreachable).
        display("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private List<String> open(Path in, String dd, int lrecl, String msg) throws IOException {
        Path p = in.resolve(dd + ".txt");
        if (!Files.isReadable(p)) {
            display(dd.equals("XREFFILE") ? msg + "35" : msg);
            errorAbend(null, "35");
        }
        return FixedRecord.readAll(p, lrecl);
    }

    /** DISPLAY message (if any), 9910-DISPLAY-IO-STATUS line, then 9999-ABEND-PROGRAM. */
    private void errorAbend(String msg, String status) {
        if (msg != null) {
            display(msg);
        }
        display("FILE STATUS IS: NNNN00" + status);
        display("ABENDING PROGRAM");
        throw new Abend();
    }

    private void getInterestRate(String group, String type, String cat) {
        rateRec = discgrp.get(group + type + cat);
        if (rateRec == null) {
            display("DISCLOSURE GROUP RECORD MISSING");
            display("TRY WITH DEFAULT GROUP CODE");
            rateRec = discgrp.get("DEFAULT   " + type + cat);
            if (rateRec == null) {
                errorAbend("ERROR READING DEFAULT DISCLOSURE GROUP", "23");
            }
        }
    }

    private void computeInterest(String tcatRec, long rate) {
        long bal = ZonedDecimal.decode(get(tcatRec, 18, 11));
        // S9(9)V99 = bal/100 * rate/100 / 1200, truncated once toward zero (long division), no ROUNDED.
        long product = bal * rate;
        long monthly = ZonedDecimal.wrap(product / 120_000L, 11);
        boolean negZero = monthly == 0 && product < 0;
        totalInt = ZonedDecimal.wrap(totalInt + monthly, 11);
        totalNegZero = totalInt == 0 && (negZero || (totalNegZero && monthly == 0));
        writeTransaction(monthly, negZero);
    }

    private void writeTransaction(long amount, boolean negZero) {
        tranIdSuffix = (tranIdSuffix + 1) % 1_000_000;
        String ts = timestamp();
        StringBuilder sb = new StringBuilder(TRAN_LEN);
        sb.append(parmDate).append(String.format("%06d", tranIdSuffix));
        sb.append("01").append("0005");
        sb.append(pad("System", 10));
        sb.append(pad("Int. for a/c " + get(acct, 1, 11), 100));
        sb.append(ZonedDecimal.encode(amount, 11, negZero));
        sb.append("000000000");
        sb.append(" ".repeat(50 + 50 + 10));
        sb.append(get(xref, 1, 16));
        sb.append(ts).append(ts);
        sb.append(" ".repeat(20));
        transact.add(sb.toString());
    }

    private String timestamp() {
        Instant now = clock.instant();
        ZonedDateTime z = now.atZone(ZoneOffset.UTC);
        int hh = now.getNano() / 10_000_000;
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", z.getYear(), z.getMonthValue(),
                z.getDayOfMonth(), z.getHour(), z.getMinute(), z.getSecond(), hh);
    }

    private void updateAccount() {
        long bal = ZonedDecimal.decode(get(acct, 13, 12));
        bal = ZonedDecimal.wrap(bal + totalInt, 12);
        boolean negZero = bal == 0 && totalNegZero && ZonedDecimal.decode(get(acct, 13, 12)) == 0
                && get(acct, 24, 1).equals("}");
        acct = put(acct, 13, ZonedDecimal.encode(bal, 12, negZero));
        acct = put(acct, 79, ZonedDecimal.encode(0, 12));
        acct = put(acct, 91, ZonedDecimal.encode(0, 12));
        accounts.put(get(acct, 1, 11), acct);
    }

    public static void main(String[] args) throws IOException {
        String parm = "";
        Path in = null;
        Path out = null;
        String clk = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--parm" -> parm = args[i + 1];
                case "--in" -> in = Path.of(args[i + 1]);
                case "--out" -> out = Path.of(args[i + 1]);
                case "--clock" -> clk = args[i + 1];
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        if (in == null || out == null) {
            System.err.println("usage: Cbact04c --parm <text> --in <dir> --out <dir> [--clock YYYYMMDDHHMMSSss]");
            System.exit(2);
        }
        Clock clock = clk == null ? Clock.systemUTC() : fixedClock(clk);
        System.exit(run(parm, clock, in, out, System.err));
    }

    /** Parses {@code YYYYMMDDHHMMSSss} (UTC) into a fixed clock. */
    public static Clock fixedClock(String s) {
        ZonedDateTime z = ZonedDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000, ZoneOffset.UTC);
        return Clock.fixed(z.toInstant(), ZoneOffset.UTC);
    }
}
