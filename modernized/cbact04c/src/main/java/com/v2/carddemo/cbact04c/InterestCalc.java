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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java port of CBACT04C (interest calculation). Behaviour-preserving, defects included. */
public final class InterestCalc {
    /** DD name to dataset path. */
    public record Files5(Path tcatbalf, Path xreffile, Path discgrp, Path acctfile, Path transact) {}

    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int S9_9V2 = 11;
    private static final int S9_10V2 = 12;

    private final Files5 files;
    private final String parmDate;
    private final Clock clock;
    private final PrintStream sysout;

    public InterestCalc(Files5 files, String parm, Clock clock, PrintStream sysout) {
        this.files = files;
        this.parmDate = Zoned.right(parm, 10); // F4
        this.clock = clock;
        this.sysout = sysout;
    }

    /** Runs the job; returns the exit code (0 or 16). */
    public int run() {
        display("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            execute();
        } catch (AbendException e) {
            return e.returnCode();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        display("END OF EXECUTION OF PROGRAM CBACT04C");
        return 0;
    }

    private void execute() throws IOException {
        List<String> tcatbal = open(files.tcatbalf(), 50, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xrefLines = open(files.xreffile(), 50, "ERROR OPENING CROSS REF FILE35");
        List<String> discLines = open(files.discgrp(), 50, "ERROR OPENING DALY REJECTS FILE"); // D4
        List<String> acctLines = open(files.acctfile(), 300, "ERROR OPENING ACCOUNT MASTER FILE");

        Collections.sort(tcatbal, (a, b) -> a.substring(0, 17).compareTo(b.substring(0, 17)));
        Collections.sort(xrefLines, (a, b) -> a.substring(0, 16).compareTo(b.substring(0, 16)));
        Map<String, String> xref = new HashMap<>(); // acct id -> first card (F3)
        for (String x : xrefLines) {
            xref.putIfAbsent(x.substring(25, 36), x.substring(0, 16));
        }
        Map<String, BigDecimal> rates = new HashMap<>();
        for (String d : discLines) {
            rates.put(d.substring(0, 16), Zoned.decode(d.substring(16, 22)));
        }
        TreeMap<String, String> accounts = new TreeMap<>();
        for (String a : acctLines) {
            accounts.put(a.substring(0, 11), a);
        }
        List<String> transactions = new ArrayList<>();

        String lastAcct = "           ";
        boolean first = true;
        BigDecimal total = ZERO;
        String account = null;
        String card = null;
        int seq = 0;

        for (String rec : tcatbal) {
            display(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    accounts.put(account.substring(0, 11), update(account, total));
                }
                first = false;
                total = ZERO;
                lastAcct = acctId;
                account = accounts.get(acctId);
                if (account == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    abend("ERROR READING ACCOUNT FILE", "23");
                }
                card = xref.get(acctId);
                if (card == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    abend("ERROR READING XREF FILE", "23");
                }
            }
            String suffix = rec.substring(11, 17); // type + cat
            String key = account.substring(112, 122) + suffix;
            BigDecimal rate = rates.get(key);
            if (rate == null) {
                display("DISCLOSURE GROUP RECORD MISSING");
                display("TRY WITH DEFAULT GROUP CODE");
                rate = rates.get(Zoned.right("DEFAULT", 10) + suffix);
                if (rate == null) {
                    abend("ERROR READING DEFAULT DISCLOSURE GROUP", "23");
                }
            }
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28));
                BigDecimal monthly = Zoned.truncate(
                        bal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN), S9_9V2); // D3, F2
                total = Zoned.truncate(total.add(monthly), S9_9V2);
                seq = (seq + 1) % 1_000_000; // F2
                transactions.add(tran(seq, account.substring(0, 11), monthly, card));
                // 1400-COMPUTE-FEES is empty (D2)
            }
        }
        // D1: the last account is never rewritten.

        FileIo.write(files.acctfile(), accounts.values());
        FileIo.write(files.transact(), transactions);
    }

    private String update(String account, BigDecimal total) {
        BigDecimal bal = Zoned.decode(account.substring(12, 24));
        bal = Zoned.truncate(bal.add(total), S9_10V2);
        StringBuilder sb = new StringBuilder(account);
        sb.replace(12, 24, Zoned.encode(bal, 12));
        sb.replace(78, 90, Zoned.encode(ZERO, 12));
        sb.replace(90, 102, Zoned.encode(ZERO, 12));
        return sb.toString();
    }

    private String tran(int seq, String acctId, BigDecimal amount, String card) {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        StringBuilder sb = new StringBuilder(350);
        sb.append(parmDate).append(Zoned.pad(Integer.toString(seq), 6, '0', true));
        sb.append("01").append("0005");
        sb.append(Zoned.right("System", 10));
        sb.append(Zoned.right("Int. for a/c " + acctId, 100));
        sb.append(Zoned.encode(amount, S9_9V2));
        sb.append("000000000");
        sb.append(" ".repeat(110));
        sb.append(card).append(ts).append(ts);
        sb.append(" ".repeat(20));
        return sb.toString();
    }

    private List<String> open(Path file, int lrecl, String message) throws IOException {
        if (!Files.isReadable(file)) {
            display(message);
            abend(null, "35");
        }
        return FileIo.read(file, lrecl);
    }

    private void abend(String message, String status) {
        if (message != null) {
            display(message);
        }
        display("FILE STATUS IS: NNNN00" + status);
        display("ABENDING PROGRAM");
        System.err.println("CEE3ABD: ABEND U0999");
        throw new AbendException(16);
    }

    private void display(String line) {
        sysout.print(line + "\n");
    }
}
