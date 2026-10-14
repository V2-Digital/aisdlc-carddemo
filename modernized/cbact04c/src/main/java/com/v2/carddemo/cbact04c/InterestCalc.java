package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Port of CBACT04C (interest calculation). Like-for-like, legacy defects D1-D5 preserved. */
public final class InterestCalc {
    private static final BigDecimal ZERO2 = new BigDecimal("0.00");

    private final String parm;
    private final Supplier<String> clock; // YYYYMMDDHHMMSSss
    private final PrintStream sysout;

    public InterestCalc(String parm, Supplier<String> clock, PrintStream sysout) {
        this.parm = Records.pad(parm, 10).substring(0, 10);
        this.clock = clock;
        this.sysout = sysout;
    }

    private static final class Abend extends RuntimeException {}

    /** @return 0 on success, 16 on abend (outputs are then not written). */
    public int run(Path in, Path out) throws IOException {
        List<String> tcat = Records.read(in.resolve("TCATBALF.txt"), Records.TCATBAL_LEN);
        tcat.sort((a, b) -> a.substring(0, 17).compareTo(b.substring(0, 17)));
        Map<String, String> xref = new HashMap<>();
        for (String r : Records.read(in.resolve("XREFFILE.txt"), Records.XREF_LEN)) {
            xref.putIfAbsent(r.substring(25, 36), r); // first card per account wins
        }
        Map<String, String> accts = new TreeMap<>();
        for (String r : Records.read(in.resolve("ACCTFILE.txt"), Records.ACCT_LEN)) accts.put(r.substring(0, 11), r);
        Map<String, String> disc = new HashMap<>();
        for (String r : Records.read(in.resolve("DISCGRP.txt"), Records.DISC_LEN)) disc.put(r.substring(0, 16), r);

        List<String> trans = new ArrayList<>();
        try {
            sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
            String lastAcct = null, acct = null, card = null;
            BigDecimal total = ZERO2;
            int seq = 0;
            for (String rec : tcat) {
                sysout.println(rec);
                String id = rec.substring(0, 11);
                if (!id.equals(lastAcct)) {
                    if (lastAcct != null) rewrite(accts, acct, total);
                    total = ZERO2;
                    lastAcct = id;
                    acct = accts.get(id);
                    if (acct == null) abend("ACCOUNT NOT FOUND: " + id, "ERROR READING ACCOUNT FILE");
                    String x = xref.get(id);
                    if (x == null) abend("ACCOUNT NOT FOUND: " + id, "ERROR READING XREF FILE");
                    card = x.substring(0, 16);
                }
                String typeCat = rec.substring(11, 17);
                String group = acct.substring(Records.GROUP, Records.GROUP + 10);
                String d = disc.get(group + typeCat);
                if (d == null) {
                    sysout.println("DISCLOSURE GROUP RECORD MISSING");
                    sysout.println("TRY WITH DEFAULT GROUP CODE");
                    d = disc.get("DEFAULT   " + typeCat);
                    if (d == null) abend(null, "ERROR READING DEFAULT DISCLOSURE GROUP");
                }
                BigDecimal rate = Zoned.decode(d.substring(16, 22), 2);
                if (rate.signum() != 0) {
                    BigDecimal bal = Zoned.decode(rec.substring(17, 28), 2);
                    BigDecimal interest = bal.multiply(rate).divide(new BigDecimal(1200), 2, RoundingMode.DOWN);
                    interest = Zoned.truncate(interest, 11);
                    total = Zoned.truncate(total.add(interest), 11);
                    seq = (seq + 1) % 1_000_000;
                    trans.add(transaction(seq, id, interest, card));
                    computeFees();
                }
            }
            // D1: the last account is never rewritten.
            sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");
        } catch (Abend a) {
            return 16;
        }
        Files.createDirectories(out);
        Records.write(out.resolve("TRANSACT.txt"), trans);
        Records.write(out.resolve("ACCTFILE.txt"), new ArrayList<>(accts.values()));
        return 0;
    }

    private void abend(String notFound, String error) {
        if (notFound != null) sysout.println(notFound);
        sysout.println(error);
        sysout.println("FILE STATUS IS: NNNN0023");
        sysout.println("ABENDING PROGRAM");
        throw new Abend();
    }

    /** D3: fees are never computed in the legacy program; hook kept for CR-2. */
    void computeFees() {}

    private static void rewrite(Map<String, String> accts, String acct, BigDecimal total) {
        BigDecimal bal = Zoned.decode(acct.substring(Records.BAL, Records.BAL + 12), 2);
        bal = Zoned.truncate(bal.add(total), 12);
        String r = Records.put(acct, Records.BAL, Zoned.encode(bal, 12));
        r = Records.put(r, Records.CREDIT, Zoned.encode(ZERO2, 12));
        r = Records.put(r, Records.DEBIT, Zoned.encode(ZERO2, 12));
        accts.put(acct.substring(0, 11), r);
    }

    private String transaction(int seq, String acctId, BigDecimal amt, String card) {
        String ts = clock.get();
        String db2 = ts.substring(0, 4) + "-" + ts.substring(4, 6) + "-" + ts.substring(6, 8) + "-"
                + ts.substring(8, 10) + "." + ts.substring(10, 12) + "." + ts.substring(12, 14) + "."
                + ts.substring(14, 16) + "0000";
        String r = " ".repeat(Records.TRAN_LEN);
        r = Records.put(r, 0, parm + String.format("%06d", seq));
        r = Records.put(r, 16, "01");
        r = Records.put(r, 18, "0005");
        r = Records.put(r, 22, "System");
        r = Records.put(r, 32, "Int. for a/c " + acctId);
        r = Records.put(r, 132, Zoned.encode(amt, 11));
        r = Records.put(r, 143, "000000000");
        r = Records.put(r, 262, card);
        r = Records.put(r, 278, db2);
        r = Records.put(r, 304, db2);
        return r;
    }
}
