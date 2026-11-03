package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java 21 rewrite of CBACT04C (interest calculation). Behaviour-preserving, defects included. */
public final class Cbact04c {
    static final int TCAT_LEN = 50;
    static final int XREF_LEN = 50;
    static final int DISC_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int TRAN_LEN = 350;

    private final PrintStream out;
    private final String parmDate;
    private final String timestamp;
    private final Map<String, String> accounts = new TreeMap<>();
    private final Map<String, String> xrefByAcct = new HashMap<>();
    private final Map<String, Long> rates = new HashMap<>();
    private final List<String> transactions = new ArrayList<>();
    private int tranIdSuffix = 0;

    // Working storage mirroring the COBOL program
    private String heldAccount;
    private String heldCardNum;
    private long totalInt;

    private Cbact04c(PrintStream out, String parm, String timestamp) {
        this.out = out;
        this.parmDate = FixedRecords.fit(parm, 10);
        this.timestamp = timestamp;
    }

    /** Thrown to end the run with exit 16 after the abend lines are printed. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    /** Runs the job; returns the exit code (0, or 16 on abend). */
    public static int run(Path tcatbalf, Path xreffile, Path discgrp, Path acctfile, Path outDir,
                          String parm, String timestamp, PrintStream sysout) throws IOException {
        Cbact04c job = new Cbact04c(sysout, parm, timestamp != null ? timestamp : clockTimestamp());
        try {
            job.execute(tcatbalf, xreffile, discgrp, acctfile);
        } catch (Abend a) {
            return 16;
        }
        Files.createDirectories(outDir);
        FixedRecords.write(outDir.resolve("TRANSACT.txt"), job.transactions);
        FixedRecords.write(outDir.resolve("ACCTFILE.txt"), job.accounts.values());
        return 0;
    }

    private void display(String s) {
        out.print(s + "\n");
    }

    private void execute(Path tcatbalf, Path xreffile, Path discgrp, Path acctfile) throws IOException {
        display("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcat = open(tcatbalf, TCAT_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xref = open(xreffile, XREF_LEN, "ERROR OPENING CROSS REF FILE");
        List<String> disc = open(discgrp, DISC_LEN, "ERROR OPENING DALY REJECTS FILE");
        List<String> accts = open(acctfile, ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE");

        for (String r : accts) {
            accounts.put(r.substring(0, 11), r);
        }
        for (String r : xref) {
            xrefByAcct.putIfAbsent(r.substring(25, 36), r);
        }
        for (String r : disc) {
            rates.put(r.substring(0, 16), Zoned.decode(r.substring(16, 22)));
        }
        tcat.sort((a, b) -> a.substring(0, 17).compareTo(b.substring(0, 17)));

        String lastAcct = "           ";
        boolean first = true;
        for (String rec : tcat) {
            display(rec);
            String acct = rec.substring(0, 11);
            if (!acct.equals(lastAcct)) {
                if (!first) {
                    updateAccount();
                } else {
                    first = false;
                }
                totalInt = 0;
                lastAcct = acct;
                getAccount(acct);
                getXref(acct);
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            long rate = interestRate(heldAccount.substring(112, 122) + type + cat, type + cat);
            if (rate != 0) {
                computeInterest(Zoned.decode(rec.substring(17, 28)), rate);
                computeFees();
            }
        }
        // D1: the COBOL "ELSE PERFORM 1050-UPDATE-ACCOUNT" is unreachable, so the last account is not rewritten.
        display("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private List<String> open(Path file, int lrecl, String message) throws IOException {
        try {
            return FixedRecords.read(file, lrecl);
        } catch (IOException e) {
            display(message);
            display("FILE STATUS IS: NNNN0035");
            abend();
            return null;
        }
    }

    private void getAccount(String acct) {
        String rec = accounts.get(acct);
        if (rec == null) {
            display("ACCOUNT NOT FOUND: " + acct);
            display("ERROR READING ACCOUNT FILE");
            display("FILE STATUS IS: NNNN0023");
            abend();
        }
        heldAccount = rec;
    }

    private void getXref(String acct) {
        String rec = xrefByAcct.get(acct);
        if (rec == null) {
            display("ACCOUNT NOT FOUND: " + acct);
            display("ERROR READING XREF FILE");
            display("FILE STATUS IS: NNNN0023");
            abend();
        }
        heldCardNum = rec.substring(0, 16);
    }

    private long interestRate(String key, String typeCat) {
        Long rate = rates.get(key);
        if (rate != null) {
            return rate;
        }
        display("DISCLOSURE GROUP RECORD MISSING");
        display("TRY WITH DEFAULT GROUP CODE");
        rate = rates.get("DEFAULT   " + typeCat);
        if (rate == null) {
            display("ERROR READING DEFAULT DISCLOSURE GROUP");
            display("FILE STATUS IS: NNNN0023");
            abend();
        }
        return rate;
    }

    /** COMPUTE WS-MONTHLY-INT = (bal * rate) / 1200, no ROUNDED: truncation toward zero, in cents. */
    static long monthlyInterest(long balCents, long rateHundredths) {
        return Zoned.wrap(balCents * rateHundredths / 120000, 11);
    }

    private void computeInterest(long balCents, long rate) {
        long monthly = monthlyInterest(balCents, rate);
        totalInt = Zoned.wrap(totalInt + monthly, 11);
        writeTransaction(monthly);
    }

    private void writeTransaction(long monthly) {
        tranIdSuffix = (tranIdSuffix + 1) % 1_000_000; // D6: silent wrap
        StringBuilder t = new StringBuilder(TRAN_LEN);
        t.append(parmDate).append(String.format("%06d", tranIdSuffix));      // 0,16
        t.append("01").append("0005").append("System    ");                  // 16,2 18,4 22,10
        t.append(FixedRecords.fit("Int. for a/c " + heldAccount.substring(0, 11), 100)); // 32,100
        t.append(Zoned.encode(monthly, 11));                                 // 132,11
        t.append("000000000");                                               // 143,9
        t.append(" ".repeat(262 - t.length()));                              // name, city, zip
        t.append(heldCardNum);                                               // 262,16
        t.append(timestamp).append(timestamp);                               // 278,26 and 304,26
        t.append(" ".repeat(TRAN_LEN - t.length()));                         // FILLER 330,20
        transactions.add(t.toString());
    }

    /** D2: 1400-COMPUTE-FEES is empty in the legacy program. */
    void computeFees() {
    }

    private void updateAccount() {
        long bal = Zoned.decode(heldAccount.substring(12, 24));
        String zero = Zoned.encode(0, 12);
        String rec = heldAccount.substring(0, 12)
                + Zoned.encode(Zoned.wrap(bal + totalInt, 12), 12)
                + heldAccount.substring(24, 78)
                + zero + zero
                + heldAccount.substring(102);
        heldAccount = rec;
        accounts.put(rec.substring(0, 11), rec);
    }

    private void abend() {
        display("ABENDING PROGRAM");
        throw new Abend();
    }

    private static String clockTimestamp() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String base = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss"));
        return base + String.format(".%02d0000", now.getNano() / 10_000_000);
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            a.put(args[i], args[i + 1]);
        }
        for (String req : new String[] {"--parm", "--TCATBALF", "--XREFFILE", "--DISCGRP", "--ACCTFILE", "--out"}) {
            if (!a.containsKey(req)) {
                System.err.println("missing " + req);
                System.exit(2);
            }
        }
        PrintStream sysout = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), false,
                java.nio.charset.StandardCharsets.ISO_8859_1);
        int rc = run(Path.of(a.get("--TCATBALF")), Path.of(a.get("--XREFFILE")), Path.of(a.get("--DISCGRP")),
                Path.of(a.get("--ACCTFILE")), Path.of(a.get("--out")), a.get("--parm"), a.get("--timestamp"), sysout);
        sysout.flush();
        System.exit(rc);
    }
}
