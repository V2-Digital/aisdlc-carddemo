package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Java rewrite of CBACT04C (INTCALC, interest calculation). Like-for-like: legacy defects are preserved.
 * Comments cite app/cbl/CBACT04C.cbl line numbers.
 */
public final class Cbact04c {
    public static final int EXIT_OK = 0;
    public static final int EXIT_ABEND = 16;

    private static final int TCAT_LEN = 50;
    private static final int XREF_LEN = 50;
    private static final int DISC_LEN = 50;
    private static final int ACCT_LEN = 300;
    private static final int TRAN_LEN = 350;

    /** File locations (one per DD). */
    public record Files_(Path tcatbalf, Path xreffile, Path discgrp, Path acctfileIn, Path acctfileOut,
                         Path transact) {}

    private final Files_ files;
    private final String parm;
    private final Clock clock;
    private final Sysout sysout;

    // KSDS images, in key order
    private TreeMap<String, String> accounts = new TreeMap<>();
    private final Map<String, String> xrefByAcct = new TreeMap<>();
    private final Map<String, String> discgrp = new TreeMap<>();
    private final List<String> tcatbal = new ArrayList<>();
    private final List<String> transactions = new ArrayList<>();

    // WORKING-STORAGE
    private String acct; // ACCOUNT-RECORD (current)
    private String xrefCard;
    private BigDecimal rate;
    private BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
    private int tranIdSuffix = 0;

    public Cbact04c(Files_ files, String parm, Clock clock, Sysout sysout) {
        this.files = files;
        this.parm = Records.pad(parm, 10); // PARM-DATE PIC X(10) (:178)
        this.clock = clock;
        this.sysout = sysout;
    }

    /** Runs the job; returns the exit code (0, or 16 on abend). Outputs are written even after an abend. */
    public int run() throws IOException {
        int rc = EXIT_OK;
        boolean opened = false;
        try {
            sysout.display("START OF EXECUTION OF PROGRAM CBACT04C"); // :181
            openFiles();
            opened = true;
            process();
            sysout.display("END OF EXECUTION OF PROGRAM CBACT04C"); // :230
        } catch (Abend a) {
            sysout.display("ABENDING PROGRAM"); // :629
            rc = EXIT_ABEND;
        }
        if (opened) {
            Records.write(files.transact(), transactions);
            Records.write(files.acctfileOut(), new ArrayList<>(accounts.values()));
        }
        return rc;
    }

    // ---- open (:234-323) -------------------------------------------------------------------------

    private void openFiles() throws IOException {
        tcatbal.addAll(load(files.tcatbalf(), TCAT_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE", ""));
        tcatbal.sort((a, b) -> key(a, 17).compareTo(key(b, 17))); // KSDS order
        for (int i = 1; i < tcatbal.size(); i++) {
            if (key(tcatbal.get(i - 1), 17).equals(key(tcatbal.get(i), 17))) duplicateKey("TCATBALF");
        }
        for (String r : load(files.xreffile(), XREF_LEN, "ERROR OPENING CROSS REF FILE", "35")) {
            String acctId = Records.field(r, 26, 11);
            String prev = xrefByAcct.get(acctId);
            // first duplicate in card-number order wins
            if (prev == null || Records.field(r, 1, 16).compareTo(Records.field(prev, 1, 16)) < 0) {
                xrefByAcct.put(acctId, r);
            }
        }
        for (String r : load(files.discgrp(), DISC_LEN, "ERROR OPENING DALY REJECTS FILE", "")) {
            if (discgrp.put(key(r, 16), r) != null) duplicateKey("DISCGRP");
        }
        for (String r : load(files.acctfileIn(), ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE", "")) {
            if (accounts.put(Records.field(r, 1, 11), r) != null) duplicateKey("ACCTFILE");
        }
    }

    private List<String> load(Path f, int lrecl, String openMsg, String statusSuffix) throws IOException {
        if (f == null || !Files.isRegularFile(f)) {
            sysout.display(openMsg + statusSuffix);
            sysout.display("FILE STATUS IS: NNNN0035"); // :642-646
            throw new Abend("open " + f);
        }
        return Records.read(f, lrecl);
    }

    private void duplicateKey(String dd) {
        sysout.display("DUPLICATE KEY IN " + dd);
        throw new Abend("duplicate key " + dd);
    }

    private static String key(String rec, int len) {
        return rec.substring(0, len);
    }

    // ---- main loop (:188-222) --------------------------------------------------------------------

    private void process() {
        String lastAcct = Records.pad("", 11);
        boolean firstTime = true;
        int recNo = 0;
        for (String rec : tcatbal) {
            recNo++;
            sysout.display(rec); // :193
            String acctId = Records.field(rec, 1, 11);
            if (!acctId.equals(lastAcct)) {
                if (!firstTime) {
                    updateAccount(); // 1050
                } else {
                    firstTime = false;
                }
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                getAcctData(acctId); // 1100
                getXrefData(acctId); // 1110
            }
            String type = Records.field(rec, 12, 2);
            String cat = Records.field(rec, 14, 4);
            String groupId = Records.field(acct, 113, 10);
            getInterestRate(groupId, type, cat); // 1200
            if (rate.signum() != 0) {
                BigDecimal bal = decode(rec, 18, 11, 2, "TCATBALF", recNo);
                computeInterest(bal); // 1300; 1400-COMPUTE-FEES is empty (:518)
            }
        }
        // D1: at EOF the loop ends without 1050-UPDATE-ACCOUNT for the last account (:219-220 unreachable).
    }

    private BigDecimal decode(String rec, int off, int len, int scale, String dd, int recNo) {
        try {
            return Overpunch.decode(Records.field(rec, off, len), scale);
        } catch (IllegalArgumentException e) {
            sysout.display("NON-NUMERIC FIELD IN " + dd + " RECORD " + recNo);
            throw new Abend(e.getMessage());
        }
    }

    // ---- 1050-UPDATE-ACCOUNT (:350) ----------------------------------------------------------------

    private void updateAccount() {
        BigDecimal bal = decode(acct, 13, 12, 2, "ACCTFILE", 0);
        bal = Overpunch.truncate(bal.add(totalInt), 12, 2); // S9(10)V99
        String r = Records.put(acct, 13, Overpunch.encode(bal, 12, 2));
        r = Records.put(r, 79, Overpunch.encode(BigDecimal.ZERO, 12, 2));
        r = Records.put(r, 91, Overpunch.encode(BigDecimal.ZERO, 12, 2));
        acct = r;
        accounts.put(Records.field(r, 1, 11), r); // REWRITE
    }

    // ---- 1100 / 1110 (:372, :393) ------------------------------------------------------------------

    private void getAcctData(String acctId) {
        acct = accounts.get(acctId);
        if (acct == null) {
            sysout.display("ACCOUNT NOT FOUND: " + acctId);
            sysout.display("ERROR READING ACCOUNT FILE");
            sysout.display("FILE STATUS IS: NNNN0023");
            throw new Abend("account " + acctId);
        }
    }

    private void getXrefData(String acctId) {
        String x = xrefByAcct.get(acctId);
        if (x == null) {
            sysout.display("ACCOUNT NOT FOUND: " + acctId);
            sysout.display("ERROR READING XREF FILE");
            sysout.display("FILE STATUS IS: NNNN0023");
            throw new Abend("xref " + acctId);
        }
        xrefCard = Records.field(x, 1, 16);
    }

    // ---- 1200-GET-INTEREST-RATE (:415) -------------------------------------------------------------

    private void getInterestRate(String groupId, String type, String cat) {
        String rec = discgrp.get(groupId + type + cat);
        if (rec == null) {
            sysout.display("DISCLOSURE GROUP RECORD MISSING");
            sysout.display("TRY WITH DEFAULT GROUP CODE");
            rec = discgrp.get(Records.pad("DEFAULT", 10) + type + cat); // 1200-A (:443)
            if (rec == null) {
                sysout.display("ERROR READING DEFAULT DISCLOSURE GROUP");
                sysout.display("FILE STATUS IS: NNNN0023");
                throw new Abend("default disclosure group");
            }
        }
        try {
            rate = Overpunch.decode(Records.field(rec, 17, 6), 2);
        } catch (IllegalArgumentException e) {
            sysout.display("NON-NUMERIC FIELD IN DISCGRP");
            throw new Abend(e.getMessage());
        }
    }

    // ---- 1300-COMPUTE-INTEREST (:462) --------------------------------------------------------------

    /** (balance * rate) / 1200, truncated toward zero to 2 dp (no ROUNDED, D3), into S9(9)V99 (D4). */
    static BigDecimal monthlyInterest(BigDecimal balance, BigDecimal rate) {
        BigDecimal m = balance.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN);
        return Overpunch.truncate(m, 11, 2);
    }

    private void computeInterest(BigDecimal bal) {
        BigDecimal monthly = monthlyInterest(bal, rate);
        totalInt = Overpunch.truncate(totalInt.add(monthly), 11, 2); // :467
        writeTransaction(monthly);
    }

    // ---- 1300-B-WRITE-TX (:473) --------------------------------------------------------------------

    private void writeTransaction(BigDecimal monthly) {
        tranIdSuffix = (tranIdSuffix + 1) % 1_000_000; // PIC 9(06), no SIZE ERROR (D4)
        String ts = clock.db2Timestamp();
        StringBuilder t = new StringBuilder(TRAN_LEN);
        t.append(parm).append(Overpunch.encodeUnsigned(tranIdSuffix, 6)); // TRAN-ID 16
        t.append("01");
        t.append("0005");
        t.append(Records.pad("System", 10));
        t.append(Records.pad("Int. for a/c " + Records.field(acct, 1, 11), 100));
        t.append(Overpunch.encode(monthly, 11, 2));
        t.append("000000000");
        t.append(" ".repeat(50)).append(" ".repeat(50)).append(" ".repeat(10));
        t.append(xrefCard);
        t.append(ts).append(ts);
        t.append(" ".repeat(20));
        transactions.add(t.toString());
    }
}
