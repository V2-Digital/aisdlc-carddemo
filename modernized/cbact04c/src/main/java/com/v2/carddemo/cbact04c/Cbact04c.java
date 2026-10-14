package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Java 21 rewrite of CBACT04C (INTCALC interest calculation). Behaviour-preserving, defects included. */
public final class Cbact04c {
    static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private static final class Abend extends RuntimeException {
        Abend() { super("ABEND U0999", null, false, false); }
    }

    private final PrintStream out;
    private final PrintStream err;
    private final Supplier<String> clock;
    private final String parmDate;

    private final TreeMap<String, String> accounts = new TreeMap<>();
    private final Map<String, String> xrefByAcct = new HashMap<>();
    private final Map<String, String> discgrp = new HashMap<>();
    private final List<String> transactions = new ArrayList<>();
    private int suffix;

    private Cbact04c(String parm, Supplier<String> clock, PrintStream out, PrintStream err) {
        this.parmDate = Records.pad(parm, 10);
        this.clock = clock;
        this.out = out;
        this.err = err;
    }

    /**
     * Runs the job. {@code clock} yields YYYYMMDDHHMMSSss on each call.
     * @return the process return code (0, or 16 on abend)
     */
    public static int run(String parm, DdFiles io, Supplier<String> clock, PrintStream out, PrintStream err)
            throws IOException {
        return new Cbact04c(parm, clock, out, err).execute(io);
    }

    private int execute(DdFiles io) throws IOException {
        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcat = Records.read(io.input("TCATBALF"), 50);
        for (String r : Records.read(io.input("XREFFILE"), 50)) {
            // AIX read returns the first card (card-key order) for an account
            xrefByAcct.merge(Records.slice(r, 26, 11), r,
                    (a, b) -> Records.slice(a, 1, 16).compareTo(Records.slice(b, 1, 16)) <= 0 ? a : b);
        }
        for (String r : Records.read(io.input("ACCTFILE"), 300)) accounts.put(Records.slice(r, 1, 11), r);
        for (String r : Records.read(io.input("DISCGRP"), 50)) discgrp.put(Records.slice(r, 1, 16), r);
        tcat.sort(Comparator.comparing(r -> Records.slice(r, 1, 17)));

        int rc = 0;
        try {
            process(tcat);
            out.println("END OF EXECUTION OF PROGRAM CBACT04C");
        } catch (Abend a) {
            err.println("CEE3ABD: ABEND U0999");
            rc = 16;
        }
        Records.write(io.output("ACCTFILE"), new ArrayList<>(accounts.values()), 300);
        Records.write(io.output("TRANSACT"), transactions, 350);
        return rc;
    }

    private void process(List<String> tcat) {
        String lastAcct = null;
        String acctRec = null;
        String xref = null;
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (String rec : tcat) {
            out.println(rec);
            String acct = Records.slice(rec, 1, 11);
            if (!acct.equals(lastAcct)) {
                if (lastAcct != null) updateAccount(lastAcct, total);
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acct;
                acctRec = accounts.get(acct);
                if (acctRec == null) fail("ACCOUNT NOT FOUND: " + acct, "ERROR READING ACCOUNT FILE");
                xref = xrefByAcct.get(acct);
                if (xref == null) fail("ACCOUNT NOT FOUND: " + acct, "ERROR READING XREF FILE");
            }
            String group = Records.slice(acctRec, 113, 10);
            String typeCat = Records.slice(rec, 12, 6);
            String d = discgrp.get(group + typeCat);
            if (d == null) {
                out.println("DISCLOSURE GROUP RECORD MISSING");
                out.println("TRY WITH DEFAULT GROUP CODE");
                d = discgrp.get(Records.pad("DEFAULT", 10) + typeCat);
                if (d == null) fail(null, "ERROR READING DEFAULT DISCLOSURE GROUP");
            }
            BigDecimal rate = Zoned.decode(Records.slice(d, 17, 6), 2);
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(Records.slice(rec, 18, 11), 2);
                BigDecimal monthly = Zoned.fit(
                        bal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN), 9);
                total = Zoned.fit(total.add(monthly), 9);
                writeTransaction(acct, acctRec, xref, monthly);
            }
        }
        // D1: the last account is never rewritten (loop exits at EOF before 1050 runs).
    }

    private void writeTransaction(String acct, String acctRec, String xref, BigDecimal monthly) {
        suffix = (suffix + 1) % 1_000_000;
        String c = clock.get();
        String ts = c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
                + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14) + "."
                + c.substring(14, 16) + "0000";
        String r = Records.pad("", 350);
        r = Records.splice(r, 1, parmDate + String.format("%06d", suffix));
        r = Records.splice(r, 17, "01");
        r = Records.splice(r, 19, "0005");
        r = Records.splice(r, 23, "System");
        r = Records.splice(r, 33, "Int. for a/c " + acct);
        r = Records.splice(r, 133, Zoned.encode(monthly, 2, 11));
        r = Records.splice(r, 144, "000000000");
        r = Records.splice(r, 263, Records.slice(xref, 1, 16));
        r = Records.splice(r, 279, ts + ts);
        transactions.add(r);
    }

    private void updateAccount(String acct, BigDecimal total) {
        String r = accounts.get(acct);
        BigDecimal bal = Zoned.decode(Records.slice(r, 13, 12), 2);
        bal = Zoned.fit(bal.add(total), 10);
        r = Records.splice(r, 13, Zoned.encode(bal, 2, 12));
        r = Records.splice(r, 79, Zoned.encode(BigDecimal.ZERO, 2, 12));
        r = Records.splice(r, 91, Zoned.encode(BigDecimal.ZERO, 2, 12));
        accounts.put(acct, r);
    }

    private void fail(String notFound, String error) {
        if (notFound != null) out.println(notFound);
        out.println(error);
        out.println("FILE STATUS IS: NNNN0023");
        out.println("ABENDING PROGRAM");
        throw new Abend();
    }
}
