package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
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

/**
 * Java rewrite of CBACT04C (interest calculation). Behaviour-preserving, including known
 * legacy defects F1-F7 (see aisdlc intent/spec): the last account is not written back,
 * interest is truncated, zero-amount transactions are written, overflow is silent.
 */
public final class Cbact04c {
    static final int TCAT_LEN = 50, XREF_LEN = 50, DISC_LEN = 50, ACCT_LEN = 300, TRAN_LEN = 350;

    private final Map<String, Path> dds;
    private final String parmDate;
    private final Clock clock;
    private final PrintStream out;

    /** DD keys: TCATBALF, XREFFILE, DISCGRP, ACCTFILE (input), ACCTFILE_OUT, TRANSACT (output). */
    public Cbact04c(Map<String, Path> dds, String parm, Clock clock, PrintStream sysout) {
        this.dds = dds;
        this.parmDate = FixedFile.pad(parm, 10);
        this.clock = clock;
        this.out = sysout;
    }

    /** Runs the job and returns the return code: 0 OK, 16 abend. */
    public int run() {
        try {
            execute();
            return 0;
        } catch (Abend a) {
            show("ABENDING PROGRAM");
            return 16;
        }
    }

    private void show(String line) {
        out.print(line + "\n");
    }

    private List<String> open(String dd, int lrecl, String errorText) {
        try {
            return FixedFile.read(dds.get(dd), lrecl);
        } catch (IOException | RuntimeException e) {
            show(errorText);
            show("FILE STATUS IS: NNNN0035");
            throw new Abend("open " + dd);
        }
    }

    private void execute() {
        show("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcat = open("TCATBALF", TCAT_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xrefRecs = open("XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE35");
        List<String> discRecs = open("DISCGRP", DISC_LEN, "ERROR OPENING DALY REJECTS FILE");
        List<String> acctRecs = open("ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE");

        // KSDS semantics: sequential read in primary-key order, random read by key.
        tcat.sort((a, b) -> a.substring(0, 17).compareTo(b.substring(0, 17)));
        TreeMap<String, String> accounts = new TreeMap<>();
        for (String r : acctRecs) {
            accounts.put(r.substring(0, 11), r);
        }
        Map<String, String> discgrp = new HashMap<>();
        for (String r : discRecs) {
            discgrp.put(r.substring(0, 16), r);
        }
        // Alternate key (account id): first card in primary-key order wins.
        List<String> sortedXref = new ArrayList<>(xrefRecs);
        sortedXref.sort((a, b) -> a.substring(0, 16).compareTo(b.substring(0, 16)));
        Map<String, String> xrefByAcct = new HashMap<>();
        for (String r : sortedXref) {
            xrefByAcct.putIfAbsent(r.substring(25, 36), r);
        }

        List<String> transactions = new ArrayList<>();
        BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
        int suffix = 0;
        String lastAcct = " ".repeat(11);
        boolean first = true;
        String acctRec = null;
        String acctId = null;
        String card = null;

        for (String rec : tcat) {
            show(rec);
            String recAcct = rec.substring(0, 11);
            if (!recAcct.equals(lastAcct)) {
                if (!first) {
                    accounts.put(acctId, updateAccount(acctRec, totalInt));
                } else {
                    first = false;
                }
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = recAcct;
                acctRec = accounts.get(recAcct);
                if (acctRec == null) {
                    show("ACCOUNT NOT FOUND: " + recAcct);
                    show("ERROR READING ACCOUNT FILE");
                    show("FILE STATUS IS: NNNN0023");
                    throw new Abend("account");
                }
                acctId = recAcct;
                String xr = xrefByAcct.get(recAcct);
                if (xr == null) {
                    show("ACCOUNT NOT FOUND: " + recAcct);
                    show("ERROR READING XREF FILE");
                    show("FILE STATUS IS: NNNN0023");
                    throw new Abend("xref");
                }
                card = xr.substring(0, 16);
            }

            String typeCat = rec.substring(11, 17);
            String drec = discgrp.get(acctRec.substring(112, 122) + typeCat);
            if (drec == null) {
                show("DISCLOSURE GROUP RECORD MISSING");
                show("TRY WITH DEFAULT GROUP CODE");
                drec = discgrp.get("DEFAULT   " + typeCat);
                if (drec == null) {
                    show("ERROR READING DEFAULT DISCLOSURE GROUP");
                    show("FILE STATUS IS: NNNN0023");
                    throw new Abend("default group");
                }
            }
            BigDecimal rate = Zoned.decode(drec.substring(16, 22));
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28));
                // Truncated (no ROUNDED) and silently cut to S9(9)V99.
                BigDecimal monthly = Zoned.truncate(
                        bal.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 9);
                totalInt = Zoned.truncate(totalInt.add(monthly), 9);
                suffix = (suffix + 1) % 1_000_000;
                transactions.add(transaction(suffix, acctId, monthly, card));
            }
            // 1400-COMPUTE-FEES: empty in the legacy program (CR-2).
        }
        // F1: no write-back of the last account after EOF, as in the legacy loop.

        try {
            FixedFile.write(dds.get("TRANSACT"), transactions, TRAN_LEN);
            FixedFile.write(dds.get("ACCTFILE_OUT"), accounts.values(), ACCT_LEN);
        } catch (IOException e) {
            show("ERROR OPENING TRANSACTION FILE");
            show("FILE STATUS IS: NNNN0035");
            throw new Abend("output");
        }
        show("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private static String updateAccount(String rec, BigDecimal totalInt) {
        BigDecimal bal = Zoned.decode(rec.substring(12, 24));
        String zero = Zoned.encode(BigDecimal.ZERO, 12);
        return rec.substring(0, 12)
                + Zoned.encode(Zoned.truncate(bal.add(totalInt), 10), 12)
                + rec.substring(24, 78)
                + zero + zero
                + rec.substring(102);
    }

    private String transaction(int suffix, String acctId, BigDecimal amount, String card) {
        String ts = timestamp();
        return parmDate + String.format("%06d", suffix)
                + "01" + "0005"
                + FixedFile.pad("System", 10)
                + FixedFile.pad("Int. for a/c " + acctId, 100)
                + Zoned.encode(amount, 11)
                + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card
                + ts + ts
                + " ".repeat(20);
    }

    /** YYYY-MM-DD-HH.MM.SS.hh0000 (F7: hundredths + 0000, not microseconds). */
    private String timestamp() {
        Instant now = clock.instant();
        LocalDateTime t = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    /** Parses YYYYMMDDHHMMSSss (the harness CARDDEMO_CURRENT_DATE form) into a fixed clock. */
    static Clock fixedClock(String s) {
        LocalDateTime t = LocalDateTime.of(
                Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    public static void main(String[] args) {
        String parm = args.length > 0 ? args[0] : "";
        Map<String, Path> dds = new HashMap<>();
        for (String dd : new String[] {"TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE", "ACCTFILE_OUT", "TRANSACT"}) {
            String p = System.getenv("DD_" + dd);
            if (p != null) {
                dds.put(dd, Path.of(p));
            }
        }
        String now = System.getenv("CARDDEMO_CURRENT_DATE");
        Clock clock = now == null ? Clock.systemUTC() : fixedClock(now);
        PrintStream sysout = new PrintStream(System.out, false, StandardCharsets.ISO_8859_1);
        int rc = new Cbact04c(dds, parm, clock, sysout).run();
        sysout.flush();
        System.exit(rc);
    }
}
