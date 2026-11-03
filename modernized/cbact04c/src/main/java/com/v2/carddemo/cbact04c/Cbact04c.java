package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java port of CBACT04C (interest calculation), behaviour-preserving including legacy defects. */
public final class Cbact04c {
    /** Raised where the COBOL program performs 9999-ABEND-PROGRAM. */
    static final class Abend extends RuntimeException {
        Abend() { super("ABEND U0999", null, false, false); }
    }

    private final Path in;
    private final Path out;
    private final String parm;
    private final Clock clock;
    private final PrintStream sysout;
    private final PrintStream syserr;

    private final Map<String, String> accounts = new TreeMap<>();
    private final Map<String, String> xrefByAcct = new TreeMap<>();
    private final Map<String, String> discgrp = new TreeMap<>();
    private final List<String> transact = new ArrayList<>();

    public Cbact04c(Path in, Path out, String parm, Clock clock, PrintStream sysout, PrintStream syserr) {
        this.in = in;
        this.out = out;
        this.parm = pad(parm, 10).substring(0, 10);
        this.clock = clock;
        this.sysout = sysout;
        this.syserr = syserr;
    }

    /** Runs the job; returns the return code (0, or 16 on abend). */
    public int run() throws IOException {
        try {
            execute();
            return 0;
        } catch (Abend a) {
            syserr.println("CEE3ABD: ABEND U0999");
            return 16;
        }
    }

    private void execute() throws IOException {
        sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcatbal = open("TCATBALF", "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xref = open("XREFFILE", "ERROR OPENING CROSS REF FILE");
        List<String> disc = open("DISCGRP", "ERROR OPENING DALY REJECTS FILE");
        List<String> acct = open("ACCTFILE", "ERROR OPENING ACCOUNT MASTER FILE");
        for (String r : xref) xrefByAcct.putIfAbsent(r.substring(25, 36), r);
        for (String r : disc) discgrp.put(r.substring(0, 16), r);
        for (String r : acct) accounts.put(r.substring(0, 11), r);

        String lastAcct = null;
        String accountRec = null;
        String card = null;
        BigDecimal totalInt = BigDecimal.ZERO;
        int suffix = 0;

        // The COBOL loop ends as soon as EOF is set, so the last account is never rewritten (D1).
        for (String raw : tcatbal) {
            String rec = pad(raw, 50);
            sysout.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (lastAcct != null) {
                    accounts.put(lastAcct.substring(0, 11), updateAccount(accountRec, totalInt));
                }
                totalInt = BigDecimal.ZERO;
                lastAcct = acctId;
                accountRec = accounts.get(acctId);
                if (accountRec == null) fail("ACCOUNT NOT FOUND: " + acctId, "ERROR READING ACCOUNT FILE");
                String x = xrefByAcct.get(acctId);
                if (x == null) fail("ACCOUNT NOT FOUND: " + acctId, "ERROR READING XREF FILE");
                card = x.substring(0, 16);
            }
            String group = pad(accountRec.substring(112, 122), 10);
            String key = group + rec.substring(11, 13) + rec.substring(13, 17);
            String d = discgrp.get(key);
            if (d == null) {
                sysout.println("DISCLOSURE GROUP RECORD MISSING");
                sysout.println("TRY WITH DEFAULT GROUP CODE");
                d = discgrp.get(pad("DEFAULT", 10) + rec.substring(11, 13) + rec.substring(13, 17));
                if (d == null) fail("ERROR READING DEFAULT DISCLOSURE GROUP");
            }
            BigDecimal rate = Zoned.decode(d.substring(16, 22), 2);
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28), 2);
                // COMPUTE without ROUNDED truncates (D2); no SIZE ERROR clause drops high-order digits (D7).
                BigDecimal monthly = Zoned.fit(
                        bal.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 11, 2);
                totalInt = Zoned.fit(totalInt.add(monthly), 11, 2);
                suffix = (suffix + 1) % 1_000_000;
                transact.add(tranRecord(suffix, accountRec.substring(0, 11), monthly, card));
            }
        }
        sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");

        Files.createDirectories(out);
        write(out.resolve("TRANSACT.txt"), transact);
        write(out.resolve("ACCTFILE.txt"), new ArrayList<>(accounts.values()));
    }

    private static String updateAccount(String rec, BigDecimal totalInt) {
        BigDecimal bal = Zoned.decode(rec.substring(12, 24), 2);
        String zero = Zoned.encode(BigDecimal.ZERO, 12, 2);
        return rec.substring(0, 12) + Zoned.encode(bal.add(totalInt), 12, 2) + rec.substring(24, 78)
                + zero + zero + rec.substring(102);
    }

    private String tranRecord(int suffix, String acctId, BigDecimal amt, String card) {
        String ts = Clock.db2(clock.now16());
        return parm + String.format("%06d", suffix) + "01" + "0005" + pad("System", 10)
                + pad("Int. for a/c " + acctId, 100) + Zoned.encode(amt, 11, 2) + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10) + card + ts + ts + " ".repeat(20);
    }

    private List<String> open(String dd, String errText) throws IOException {
        Path p = in.resolve(dd + ".txt");
        if (!Files.exists(p)) {
            sysout.println(errText + (dd.equals("XREFFILE") ? "35" : ""));
            fail();
        }
        List<String> lines = new ArrayList<>();
        for (String l : Files.readString(p, StandardCharsets.ISO_8859_1).split("\n")) {
            if (l.endsWith("\r")) l = l.substring(0, l.length() - 1);
            if (!l.isEmpty()) lines.add(l);
        }
        return lines;
    }

    /** Prints the messages, FILE STATUS line (status 23, or 35 for missing files) and abends. */
    private void fail(String... messages) {
        for (String m : messages) sysout.println(m);
        if (messages.length > 0) sysout.println("FILE STATUS IS: NNNN0023");
        else sysout.println("FILE STATUS IS: NNNN0035");
        sysout.println("ABENDING PROGRAM");
        throw new Abend();
    }

    private static void write(Path p, List<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        Files.writeString(p, sb, StandardCharsets.ISO_8859_1);
    }

    private static String pad(String s, int n) {
        return s.length() >= n ? s : s + " ".repeat(n - s.length());
    }
}
