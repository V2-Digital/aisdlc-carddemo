package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java 21 rewrite of CBACT04C (INTCALC): monthly interest per category balance. Behaviour-preserving. */
public final class Cbact04c {
    static final int RC_ABEND = 16;
    private static final BigDecimal DIVISOR = BigDecimal.valueOf(1200);
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final String parmDate;
    private final Clock clock;
    private final PrintStream sysout;
    private final Map<String, String> xrefCardByAcct = new HashMap<>();
    private final Map<String, String> rateByKey = new HashMap<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();
    private final List<String> transactions = new ArrayList<>();
    private int tranIdSuffix;

    private Cbact04c(String parm, Clock clock, PrintStream sysout) {
        this.parmDate = FixedFile.pad(parm, 10).substring(0, 10);
        this.clock = clock;
        this.sysout = sysout;
    }

    /** Runs the job. Returns the exit code (0, or 16 on abend; no output files are written on abend). */
    public static int run(String parm, Path inDir, Path outDir, Clock clock, PrintStream sysout, PrintStream syserr)
            throws IOException {
        try {
            new Cbact04c(parm, clock, sysout).execute(inDir, outDir);
            return 0;
        } catch (Abend a) {
            a.lines().forEach(sysout::println);
            sysout.println("ABENDING PROGRAM");
            syserr.println("CEE3ABD: ABEND U0999");
            return RC_ABEND;
        }
    }

    private void execute(Path inDir, Path outDir) throws IOException {
        sysout.println("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcatbal = open(inDir, "TCATBALF", 50, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        for (String x : open(inDir, "XREFFILE", 50, "ERROR OPENING CROSS REF FILE")) {
            String acct = x.substring(25, 36);
            String card = x.substring(0, 16);
            xrefCardByAcct.merge(acct, card, (a, b) -> a.compareTo(b) <= 0 ? a : b);
        }
        for (String d : open(inDir, "DISCGRP", 50, "ERROR OPENING DALY REJECTS FILE")) {
            rateByKey.put(d.substring(0, 16), d.substring(16, 22));
        }
        for (String a : open(inDir, "ACCTFILE", 300, "ERROR OPENING ACCOUNT MASTER FILE")) {
            accounts.put(a.substring(0, 11), a);
        }

        tcatbal.sort((a, b) -> a.substring(0, 17).compareTo(b.substring(0, 17)));
        String lastAcct = "           ";
        String account = null;
        String card = null;
        BigDecimal total = ZERO;
        boolean first = true;
        for (String rec : tcatbal) {
            sysout.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    accounts.put(lastAcct, updateAccount(account, total));
                }
                first = false;
                total = ZERO;
                lastAcct = acctId;
                account = accounts.get(acctId);
                if (account == null) {
                    throw notFound(acctId, "ERROR READING ACCOUNT FILE");
                }
                card = xrefCardByAcct.get(acctId);
                if (card == null) {
                    throw notFound(acctId, "ERROR READING XREF FILE");
                }
            }
            BigDecimal rate = interestRate(account.substring(112, 122), rec.substring(11, 17));
            if (rate.signum() != 0) {
                BigDecimal bal = Overpunch.decode(rec.substring(17, 28), 2);
                BigDecimal monthly = fit(bal.multiply(rate).divide(DIVISOR, 2, RoundingMode.DOWN), 9);
                total = fit(total.add(monthly), 9);
                transactions.add(transaction(acctId, card, monthly));
            }
            // 1400-COMPUTE-FEES is empty in the legacy program
        }
        // D1 (preserved): the legacy ELSE PERFORM 1050-UPDATE-ACCOUNT is unreachable, so the last account is not rewritten.

        Files.createDirectories(outDir);
        FixedFile.write(outDir.resolve("TRANSACT.txt"), transactions);
        FixedFile.write(outDir.resolve("ACCTFILE.txt"), new ArrayList<>(accounts.values()));
        sysout.println("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private static List<String> open(Path dir, String dd, int lrecl, String message) throws IOException {
        Path f = dir.resolve(dd + ".txt");
        if (!Files.isRegularFile(f)) {
            // GnuCOBOL file status 35; the XREF message appends the status inline
            String text = dd.equals("XREFFILE") ? message + "35" : message;
            throw new Abend(text, "FILE STATUS IS: NNNN0035");
        }
        return FixedFile.read(f, lrecl);
    }

    private static Abend notFound(String acctId, String error) {
        return new Abend("ACCOUNT NOT FOUND: " + acctId, error, "FILE STATUS IS: NNNN0023");
    }

    /** 1200-GET-INTEREST-RATE: group lookup, falling back to DEFAULT on a miss. */
    private BigDecimal interestRate(String groupId, String typeCat) {
        String rate = rateByKey.get(groupId + typeCat);
        if (rate == null) {
            sysout.println("DISCLOSURE GROUP RECORD MISSING");
            sysout.println("TRY WITH DEFAULT GROUP CODE");
            rate = rateByKey.get("DEFAULT   " + typeCat);
            if (rate == null) {
                throw new Abend("ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023");
            }
        }
        return Overpunch.decode(rate, 2);
    }

    /** 1050-UPDATE-ACCOUNT: balance += total, cycle credit/debit = 0. */
    private static String updateAccount(String rec, BigDecimal total) {
        BigDecimal bal = fit(Overpunch.decode(rec.substring(12, 24), 2).add(total), 10);
        String zero = Overpunch.encode(ZERO, 2, 12);
        return rec.substring(0, 12) + Overpunch.encode(bal, 2, 12) + rec.substring(24, 78) + zero + zero
                + rec.substring(102);
    }

    /** 1300-B-WRITE-TX: builds the 350-byte TRAN-RECORD. */
    private String transaction(String acctId, String card, BigDecimal amount) {
        tranIdSuffix = (tranIdSuffix + 1) % 1_000_000;
        String ts = timestamp();
        return parmDate + String.format("%06d", tranIdSuffix)
                + "01" + "0005" + FixedFile.pad("System", 10)
                + FixedFile.pad("Int. for a/c " + acctId, 100)
                + Overpunch.encode(amount, 2, 11)
                + "000000000" + " ".repeat(110)
                + card + ts + ts + " ".repeat(20);
    }

    /** DB2-style timestamp: yyyy-MM-dd-HH.mm.ss.hh0000 (hundredths, then the literal 0000). */
    private String timestamp() {
        LocalDateTime t = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    /** Keeps the low-order {@code intDigits} integer digits (no SIZE ERROR clause: high-order truncation). */
    private static BigDecimal fit(BigDecimal v, int intDigits) {
        BigDecimal mod = BigDecimal.TEN.pow(intDigits);
        return v.remainder(mod).setScale(2);
    }

    /** CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss, or the system clock (UTC) when unset. */
    static Clock clockFromEnv(String s) {
        if (s == null || s.length() < 16) {
            return Clock.systemUTC();
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>");
            System.exit(2);
        }
        int rc = run(args[0], Path.of(args[1]), Path.of(args[2]), clockFromEnv(System.getenv("CARDDEMO_CURRENT_DATE")),
                System.out, System.err);
        System.out.flush();
        System.exit(rc);
    }
}
