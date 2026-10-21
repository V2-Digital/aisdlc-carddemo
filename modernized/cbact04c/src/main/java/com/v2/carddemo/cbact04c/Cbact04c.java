package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Java 21 rewrite of CBACT04C (interest calculation), behaviour-preserving incl. legacy defects. */
public final class Cbact04c {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int DISC_LEN = 50;

    private final PrintStream sysout;
    private final PrintStream syserr;

    private Cbact04c(PrintStream sysout, PrintStream syserr) {
        this.sysout = sysout;
        this.syserr = syserr;
    }

    /**
     * @param clock supplies FUNCTION CURRENT-DATE as YYYYMMDDHHMMSSss, called once per transaction
     * @return exit code, 0 or 16 on abend
     */
    public static int run(Path in, Path out, String parm, Supplier<String> clock,
                          PrintStream sysout, PrintStream syserr) throws IOException {
        return new Cbact04c(sysout, syserr).execute(in, out, parm, clock);
    }

    private int execute(Path in, Path out, String parm, Supplier<String> clock) throws IOException {
        String parmDate = pad(parm, 10);
        sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            // R1: open in COBOL order
            List<String> tcatbal = open(in, "TCATBALF", TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE", false);
            List<String> xrefLines = open(in, "XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE", true);
            List<String> discLines = open(in, "DISCGRP", DISC_LEN, "ERROR OPENING DALY REJECTS FILE", false);
            List<String> acctLines = open(in, "ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE", false);
            Files.createDirectories(out);
            if (!Files.isDirectory(out)) {
                openFailed("ERROR OPENING TRANSACTION FILE", "30");
            }

            Map<String, String> accounts = new TreeMap<>();
            for (String l : acctLines) {
                accounts.putIfAbsent(l.substring(0, 11), l);
            }
            Map<String, String> byCard = new TreeMap<>();
            for (String l : xrefLines) {
                byCard.putIfAbsent(l.substring(0, 16), l);
            }
            Map<String, String> xrefByAcct = new HashMap<>();
            for (String l : byCard.values()) {
                xrefByAcct.putIfAbsent(l.substring(25, 36), l);
            }
            Map<String, String> disc = new HashMap<>();
            for (String l : discLines) {
                disc.put(l.substring(0, 16), l);
            }

            List<String> transactions = new ArrayList<>();
            String lastAcct = null; // WS-LAST-ACCT-NUM; first-time flag
            String account = null;
            String xref = null;
            BigDecimal total = BigDecimal.ZERO.setScale(2);
            int suffix = 0;

            for (String rec : tcatbal) {
                sysout.println(rec);
                String acctId = rec.substring(0, 11);
                if (!acctId.equals(lastAcct)) {
                    if (lastAcct != null) {
                        updateAccount(accounts, account, total);
                    }
                    total = BigDecimal.ZERO.setScale(2);
                    lastAcct = acctId;
                    account = accounts.get(acctId);
                    if (account == null) {
                        lookupFailed(acctId, "ERROR READING ACCOUNT FILE");
                    }
                    xref = xrefByAcct.get(acctId);
                    if (xref == null) {
                        lookupFailed(acctId, "ERROR READING XREF FILE");
                    }
                }
                String groupId = pad(account.substring(112, 122), 10);
                String type = rec.substring(11, 13);
                String cat = rec.substring(13, 17);
                String d = disc.get(groupId + type + cat);
                if (d == null) {
                    sysout.println("DISCLOSURE GROUP RECORD MISSING");
                    sysout.println("TRY WITH DEFAULT GROUP CODE");
                    d = disc.get(pad("DEFAULT", 10) + type + cat);
                    if (d == null) {
                        sysout.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                        statusAndAbend("23");
                    }
                }
                BigDecimal rate = Zoned.parse(d.substring(16, 22), 2);
                if (rate.signum() != 0) {
                    BigDecimal bal = Zoned.parse(rec.substring(17, 28), 2);
                    BigDecimal interest = Zoned.truncate(
                        bal.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 11);
                    total = Zoned.truncate(total.add(interest), 11);
                    suffix++;
                    transactions.add(transaction(parmDate, suffix, acctId, interest, xref.substring(0, 16),
                                                 timestamp(clock.get())));
                }
                // 1400-COMPUTE-FEES: empty in legacy (CR-2)
            }
            // D1: the final account is never rewritten (the ELSE PERFORM 1050 is unreachable).

            sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");
            write(out.resolve("TRANSACT.txt"), transactions);
            write(out.resolve("ACCTFILE.txt"), new ArrayList<>(accounts.values()));
            return 0;
        } catch (Abend a) {
            syserr.println("CEE3ABD: ABEND U0999");
            return 16;
        }
    }

    private static void updateAccount(Map<String, String> accounts, String account, BigDecimal total) {
        BigDecimal bal = Zoned.parse(account.substring(12, 24), 2);
        String zero = Zoned.format(BigDecimal.ZERO.setScale(2), 12);
        String r = account.substring(0, 12) + Zoned.format(bal.add(total), 12)
            + account.substring(24, 78) + zero + zero + account.substring(102);
        accounts.put(account.substring(0, 11), r);
    }

    private static String transaction(String parmDate, int suffix, String acctId, BigDecimal amt,
                                      String card, String ts) {
        return parmDate + String.format("%06d", suffix)
            + "01" + "0005" + pad("System", 10)
            + pad("Int. for a/c " + acctId, 100)
            + Zoned.format(amt, 11)
            + "000000000" + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
            + card + ts + ts + " ".repeat(20);
    }

    /** CURRENT-DATE YYYYMMDDHHMMSSss to DB2 format YYYY-MM-DD-HH.MM.SS.hh0000. */
    private static String timestamp(String c) {
        return c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
            + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14) + "."
            + c.substring(14, 16) + "0000";
    }

    private List<String> open(Path in, String dd, int lrecl, String message, boolean statusOnMessage)
            throws IOException {
        try {
            List<String> lines = new ArrayList<>();
            for (String l : Files.readAllLines(in.resolve(dd + ".txt"), StandardCharsets.ISO_8859_1)) {
                l = l.endsWith("\r") ? l.substring(0, l.length() - 1) : l;
                lines.add(pad(l, lrecl).substring(0, lrecl));
            }
            return lines;
        } catch (NoSuchFileException e) {
            openFailed(statusOnMessage ? message + "35" : message, "35");
            return null;
        }
    }

    private void openFailed(String message, String status) {
        sysout.println(message);
        statusAndAbend(status);
    }

    private void lookupFailed(String acctId, String message) {
        sysout.println("ACCOUNT NOT FOUND: " + acctId);
        sysout.println(message);
        statusAndAbend("23");
    }

    private void statusAndAbend(String status) {
        sysout.println("FILE STATUS IS: NNNN00" + status);
        sysout.println("ABENDING PROGRAM");
        throw new Abend();
    }

    private static void write(Path file, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.writeString(file, sb, StandardCharsets.ISO_8859_1);
    }

    private static String pad(String s, int n) {
        return s.length() >= n ? s : s + " ".repeat(n - s.length());
    }
}
