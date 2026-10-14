package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java 21 rewrite of CBACT04C (interest calculation). Behaviour-preserving, defects included. */
public final class Cbact04c {
    static final int ACCT_LEN = 300;
    static final int TRAN_LEN = 350;
    static final String[] DDS = {"TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE", "TRANSACT"};

    // ACCTFILE offsets (0-based) from CVACT01Y
    static final int BAL = 12;
    static final int CYC_CREDIT = 78;
    static final int CYC_DEBIT = 90;
    static final int GROUP_ID = 112;

    private final Map<String, Path> dd;
    private final Clock clock;
    private final PrintStream out;

    Cbact04c(Map<String, Path> dd, Clock clock, PrintStream out) {
        this.dd = dd;
        this.clock = clock;
        this.out = out;
    }

    public static int run(String parm, Map<String, Path> dd, Clock clock, PrintStream sysout) {
        return new Cbact04c(dd, clock, sysout).execute(parm);
    }

    public static void main(String[] args) {
        Map<String, Path> dd = new HashMap<>();
        for (String name : DDS) {
            String v = System.getenv("DD_" + name);
            if (v != null) {
                dd.put(name, Path.of(v));
            }
        }
        String parm = args.length > 0 ? args[0] : "";
        String fixed = System.getProperty("carddemo.clock"); // e.g. 2022-07-18T00:00:00 (equivalence runs)
        Clock clock = fixed == null ? Clock.systemDefaultZone()
                : Clock.fixed(LocalDateTime.parse(fixed).toInstant(java.time.ZoneOffset.UTC), java.time.ZoneOffset.UTC);
        int rc = run(parm, dd, clock, System.out);
        System.out.flush();
        System.exit(rc);
    }

    private int execute(String parm) {
        String parmDate = FixedFile.pad(parm, 10);
        TreeMap<String, String> accounts = new TreeMap<>();
        List<String> transactions = new ArrayList<>();
        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            return process(parmDate, accounts, transactions);
        } catch (Abend a) {
            out.println("ABENDING PROGRAM");
            // KSDS REWRITE and sequential WRITE took effect before the abend
            try {
                persist(accounts, transactions);
            } catch (IOException e) {
                // best effort
            }
            return 16;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private List<String> open(String name, int lrecl, String message) {
        Path p = dd.get(name);
        try {
            if (p == null || !Files.isReadable(p)) {
                throw new IOException("missing");
            }
            return FixedFile.read(p, lrecl);
        } catch (IOException e) {
            out.println(message);
            out.println("FILE STATUS IS: NNNN0035");
            throw new Abend(message);
        }
    }

    private int process(String parmDate, TreeMap<String, String> accounts, List<String> transactions)
            throws IOException {
        List<String> tcatbal = open("TCATBALF", 50, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xref = open("XREFFILE", 50, "ERROR OPENING CROSS REF FILE");
        List<String> disc = open("DISCGRP", 50, "ERROR OPENING DALY REJECTS FILE");
        List<String> acctRecs = open("ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE");
        Path tranOut = dd.get("TRANSACT");
        if (tranOut == null || tranOut.getParent() != null && !Files.isWritable(tranOut.getParent())) {
            out.println("ERROR OPENING TRANSACTION FILE");
            out.println("FILE STATUS IS: NNNN0035");
            throw new Abend("open TRANSACT");
        }

        for (String r : acctRecs) {
            accounts.put(r.substring(0, 11), r);
        }
        // AIX on account id: first card in primary-key (card number) order
        Map<String, String> cardByAcct = new HashMap<>();
        new TreeMap<String, String>(xrefByCard(xref)).forEach((card, acct) -> cardByAcct.putIfAbsent(acct, card));
        Map<String, BigDecimal> rates = new HashMap<>();
        for (String r : disc) {
            rates.put(r.substring(0, 16), Zoned.decode(r.substring(16, 22), 2));
        }

        String lastAcct = " ".repeat(11);
        boolean first = true;
        BigDecimal total = BigDecimal.ZERO;
        int suffix = 0;
        String acct = null;
        String card = null;

        for (String rec : tcatbal) {
            out.println(rec);
            String acctId = rec.substring(0, 11);
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            BigDecimal bal = Zoned.decode(rec.substring(17, 28), 2);

            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    acct = rewrite(acct, total);
                    accounts.put(acct.substring(0, 11), acct);
                } else {
                    first = false;
                }
                total = BigDecimal.ZERO;
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    out.println("ERROR READING ACCOUNT FILE");
                    out.println("FILE STATUS IS: NNNN0023");
                    throw new Abend("account");
                }
                card = cardByAcct.get(acctId);
                if (card == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    out.println("ERROR READING XREF FILE");
                    out.println("FILE STATUS IS: NNNN0023");
                    throw new Abend("xref");
                }
            }

            String group = acct.substring(GROUP_ID, GROUP_ID + 10);
            BigDecimal rate = rates.get(group + type + cat);
            if (rate == null) {
                out.println("DISCLOSURE GROUP RECORD MISSING");
                out.println("TRY WITH DEFAULT GROUP CODE");
                rate = rates.get(FixedFile.pad("DEFAULT", 10) + type + cat);
                if (rate == null) {
                    out.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                    out.println("FILE STATUS IS: NNNN0023");
                    throw new Abend("default group");
                }
            }

            if (rate.signum() != 0) {
                BigDecimal monthly = Zoned.truncate(
                        bal.multiply(rate).divide(BigDecimal.valueOf(1200), 10, RoundingMode.DOWN), 9, 2);
                total = Zoned.truncate(total.add(monthly), 9, 2);
                suffix = (suffix + 1) % 1_000_000;
                transactions.add(transaction(parmDate, suffix, acctId, monthly, card));
                // 1400-COMPUTE-FEES: empty in the legacy program (D3)
            }
        }
        // D1: the current (last) account is never rewritten at EOF.
        persist(accounts, transactions);
        out.println("END OF EXECUTION OF PROGRAM CBACT04C");
        return 0;
    }

    private static Map<String, String> xrefByCard(List<String> xref) {
        Map<String, String> m = new HashMap<>();
        for (String r : xref) {
            m.put(r.substring(0, 16), r.substring(25, 36));
        }
        return m;
    }

    /** 1050-UPDATE-ACCOUNT on the raw record. */
    private static String rewrite(String acct, BigDecimal total) {
        BigDecimal bal = Zoned.decode(acct.substring(BAL, BAL + 12), 2);
        StringBuilder sb = new StringBuilder(acct);
        sb.replace(BAL, BAL + 12, Zoned.encode(bal.add(total), 10, 2));
        String zero = Zoned.encode(BigDecimal.ZERO, 10, 2);
        sb.replace(CYC_CREDIT, CYC_CREDIT + 12, zero);
        sb.replace(CYC_DEBIT, CYC_DEBIT + 12, zero);
        return sb.toString();
    }

    private String transaction(String parmDate, int suffix, String acctId, BigDecimal amt, String card) {
        String ts = timestamp();
        return parmDate + String.format("%06d", suffix)
                + "01" + "0005"
                + FixedFile.pad("System", 10)
                + FixedFile.pad("Int. for a/c " + acctId, 100)
                + Zoned.encode(amt, 9, 2)
                + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card
                + ts + ts
                + " ".repeat(20);
    }

    private String timestamp() {
        LocalDateTime t = LocalDateTime.now(clock);
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    private void persist(TreeMap<String, String> accounts, List<String> transactions) throws IOException {
        if (accounts.isEmpty() || dd.get("ACCTFILE") == null) {
            return;
        }
        FixedFile.write(dd.get("ACCTFILE"), new ArrayList<>(accounts.values()));
        FixedFile.write(dd.get("TRANSACT"), transactions);
    }
}
