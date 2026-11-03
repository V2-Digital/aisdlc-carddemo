package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java port of CBACT04C (INTCALC): monthly interest per transaction-category balance. Like-for-like, defects kept. */
public final class InterestCalc {
    static final int RC_ABEND = 16;
    private static final BigDecimal LIMIT_9 = new BigDecimal(BigInteger.TEN.pow(9));
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    /** Thrown where the COBOL performs 9999-ABEND-PROGRAM. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    private final Path in;
    private final Path out;
    private final String parm;
    private final Clock clock;
    private final PrintStream stdout;
    private final List<String> sysout = new ArrayList<>();

    public InterestCalc(Path in, Path out, String parm, Clock clock, PrintStream stdout) {
        this.in = in;
        this.out = out;
        this.parm = Records.pad(parm, 10);
        this.clock = clock;
        this.stdout = stdout;
    }

    /** Runs the job; returns the return code (0, or 16 on abend). */
    public int run() throws IOException {
        int rc = 0;
        List<String> transact = new ArrayList<>();
        Map<String, String> accounts = new TreeMap<>();
        try {
            execute(transact, accounts);
            Records.write(out.resolve("TRANSACT.txt"), transact);
            Records.write(out.resolve("ACCTFILE.txt"), new ArrayList<>(accounts.values()));
        } catch (Abend e) {
            rc = RC_ABEND;
        }
        Records.write(out.resolve("SYSOUT.txt"), sysout);
        Files.writeString(out.resolve("RC.txt"), rc + "\n");
        return rc;
    }

    private void display(String s) {
        sysout.add(s);
        if (stdout != null) {
            stdout.println(s);
        }
    }

    private void abend(String status) {
        display("FILE STATUS IS: NNNN" + status);
        display("ABENDING PROGRAM");
        throw new Abend();
    }

    private List<String> open(String dd, int lrecl, String message) throws IOException {
        Path f = in.resolve(dd + ".txt");
        if (!Files.isReadable(f)) {
            display(message);
            abend("0035");
        }
        return Records.read(f, lrecl);
    }

    private void execute(List<String> transact, Map<String, String> accounts) throws IOException {
        display("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcatbal = open("TCATBALF", 50, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xref = open("XREFFILE", 50, "ERROR OPENING CROSS REF FILE" + "35" /* COBOL appends the 2-byte status; 35 = file missing */);
        List<String> discgrp = open("DISCGRP", 50, "ERROR OPENING DALY REJECTS FILE");
        for (String rec : open("ACCTFILE", 300, "ERROR OPENING ACCOUNT MASTER FILE")) {
            accounts.put(Records.field(rec, 1, 11), rec);
        }
        // KSDS read order: primary key acct(11)+type(2)+cat(4); stable for duplicate keys
        tcatbal.sort(Comparator.comparing(r -> Records.field(r, 1, 17)));

        Map<String, String> rates = new TreeMap<>();
        for (String r : discgrp) {
            rates.putIfAbsent(Records.field(r, 1, 16), r);
        }

        String lastAcct = " ".repeat(11);
        boolean firstTime = true;
        String account = null;
        String card = null;
        BigDecimal totalInt = BigDecimal.ZERO;
        int suffix = 0;

        for (String row : tcatbal) {
            display(row);
            String acctId = Records.field(row, 1, 11);
            if (!acctId.equals(lastAcct)) {
                if (!firstTime) {
                    account = updateAccount(account, totalInt);
                    accounts.put(Records.field(account, 1, 11), account);
                } else {
                    firstTime = false;
                }
                totalInt = BigDecimal.ZERO;
                lastAcct = acctId;
                account = accounts.get(acctId);
                if (account == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING ACCOUNT FILE");
                    abend("0023");
                }
                card = null;
                for (String x : xref) {
                    if (Records.field(x, 26, 11).equals(acctId)) {
                        card = Records.field(x, 1, 16);
                        break;
                    }
                }
                if (card == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING XREF FILE");
                    abend("0023");
                }
            }
            String type = Records.field(row, 12, 2);
            String cat = Records.field(row, 14, 4);
            String group = Records.field(account, 113, 10);
            String rateRec = rates.get(group + type + cat);
            if (rateRec == null) {
                display("DISCLOSURE GROUP RECORD MISSING");
                display("TRY WITH DEFAULT GROUP CODE");
                rateRec = rates.get("DEFAULT   " + type + cat);
                if (rateRec == null) {
                    display("ERROR READING DEFAULT DISCLOSURE GROUP");
                    abend("0023");
                }
            }
            BigDecimal rate = Zoned.decode(Records.field(rateRec, 17, 6));
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(Records.field(row, 18, 11));
                BigDecimal monthly = bal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN)
                        .remainder(LIMIT_9);
                totalInt = totalInt.add(monthly).remainder(LIMIT_9);
                suffix = (suffix + 1) % 1_000_000;
                transact.add(transaction(suffix, acctId, monthly, card));
                // 1400-COMPUTE-FEES: not implemented in the legacy program (R2)
            }
        }
        // R1: the held account is never rewritten at EOF (COBOL's ELSE branch is unreachable)
        display("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private static String updateAccount(String acct, BigDecimal totalInt) {
        BigDecimal bal = Zoned.decode(Records.field(acct, 13, 12)).add(totalInt);
        String zero = Zoned.encode(BigDecimal.ZERO, 12);
        acct = Records.put(acct, 13, Zoned.encode(bal, 12));
        acct = Records.put(acct, 79, zero);
        return Records.put(acct, 91, zero);
    }

    private String transaction(int suffix, String acctId, BigDecimal amt, String card) {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        String r = " ".repeat(350);
        r = Records.put(r, 1, parm + String.format("%06d", suffix));
        r = Records.put(r, 17, "01");
        r = Records.put(r, 19, "0005");
        r = Records.put(r, 23, Records.pad("System", 10));
        r = Records.put(r, 33, Records.pad("Int. for a/c " + acctId, 100));
        r = Records.put(r, 133, Zoned.encode(amt, 11));
        r = Records.put(r, 144, "000000000");
        r = Records.put(r, 263, card);
        r = Records.put(r, 279, ts);
        return Records.put(r, 305, ts);
    }
}
