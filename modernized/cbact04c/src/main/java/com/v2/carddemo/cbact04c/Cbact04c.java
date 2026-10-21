package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Interest calculation batch, a paragraph-for-paragraph port of app/cbl/CBACT04C.cbl. */
public final class Cbact04c {
    /** Program abend (CEE3ABD U0999); exit code 16. */
    static final class Abend extends RuntimeException {
        Abend() { super("ABEND U0999", null, false, false); }
    }

    private final Map<String, Path> dd;
    private final String parmDate;
    private final Supplier<String> clock;
    private final PrintStream sysout;

    private List<String> tcatbal;
    private final Map<String, String> xref = new HashMap<>();
    private final Map<String, String> discgrp = new HashMap<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();
    private final List<String> transact = new ArrayList<>();

    private String account;        // ACCOUNT-RECORD
    private String xrefCard;       // XREF-CARD-NUM
    private BigDecimal rate;       // DIS-INT-RATE
    private BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
    private int tranIdSuffix;

    /**
     * @param dd        dataset path by DD name (TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT)
     * @param parm      PARM text; first 10 chars are PARM-DATE
     * @param clock     returns FUNCTION CURRENT-DATE as YYYYMMDDHHMMSSss
     * @param sysout    DISPLAY output
     */
    public Cbact04c(Map<String, Path> dd, String parm, Supplier<String> clock, PrintStream sysout) {
        this.dd = dd;
        this.parmDate = RecordFile.pad(parm, 10);
        this.clock = clock;
        this.sysout = sysout;
    }

    /** Runs the job; returns the exit code (0, or 16 on abend). Partial outputs are flushed on abend. */
    public int run() {
        int rc = 0;
        boolean opened = false;
        try {
            sysout.print("START OF EXECUTION OF PROGRAM CBACT04C\n");
            open();
            opened = true;
            mainLoop();
            sysout.print("END OF EXECUTION OF PROGRAM CBACT04C\n");
        } catch (Abend e) {
            System.err.println("CEE3ABD: ABEND U0999");
            rc = 16;
        }
        if (opened) {
            try {
                RecordFile.write(dd.get("TRANSACT"), transact);
                RecordFile.write(dd.get("ACCTFILE"), new ArrayList<>(accounts.values()));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        sysout.flush();
        return rc;
    }

    private void mainLoop() {
        String lastAcct = "";
        boolean firstTime = true;
        for (String rec : tcatbal) {
            sysout.print(rec + "\n");
            String acct = Records.tcatAcct(rec);
            if (!acct.equals(lastAcct)) {
                if (!firstTime) {
                    updateAccount();
                }
                firstTime = false;
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acct;
                getAcctData(acct);
                getXrefData(acct);
            }
            getInterestRate(Records.acctGroup(account), Records.tcatType(rec), Records.tcatCat(rec));
            if (rate.signum() != 0) {
                computeInterest(Records.tcatBal(rec));
                computeFees();
            }
        }
        // D1 (preserved): the COBOL loop never reaches its final 1050-UPDATE-ACCOUNT, so the
        // last account's interest is not rewritten.
    }

    private void open() {
        tcatbal = load("TCATBALF", Records.TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE", "");
        tcatbal.sort((a, b) -> Records.tcatKey(a).compareTo(Records.tcatKey(b)));  // KSDS key order
        for (String r : load("XREFFILE", Records.XREF_LEN, "ERROR OPENING CROSS REF FILE", "35")) {
            xref.putIfAbsent(Records.xrefAcct(r), r);
        }
        for (String r : load("DISCGRP", Records.DISCGRP_LEN, "ERROR OPENING DALY REJECTS FILE", "")) {
            discgrp.putIfAbsent(Records.discKey(r), r);
        }
        for (String r : load("ACCTFILE", Records.ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE", "")) {
            accounts.put(Records.acctId(r), r);
        }
        if (dd.get("TRANSACT") == null) {
            openFailed("ERROR OPENING TRANSACTION FILE", "");
        }
    }

    private List<String> load(String name, int lrecl, String message, String displayStatus) {
        Path p = dd.get(name);
        try {
            if (p == null) {
                throw new IOException("DD " + name + " not set");
            }
            return RecordFile.read(p, lrecl);
        } catch (IOException e) {
            openFailed(message, displayStatus);
            return null;
        }
    }

    private void openFailed(String message, String displayStatus) {
        sysout.print(message + displayStatus + "\n");
        abend("0035");
    }

    private void abend(String status) {
        sysout.print("FILE STATUS IS: NNNN" + status + "\n");
        sysout.print("ABENDING PROGRAM\n");
        throw new Abend();
    }

    private void getAcctData(String acctId) {
        account = accounts.get(acctId);
        if (account == null) {
            sysout.print("ACCOUNT NOT FOUND: " + acctId + "\n");
            sysout.print("ERROR READING ACCOUNT FILE\n");
            abend("0023");
        }
    }

    private void getXrefData(String acctId) {
        String r = xref.get(acctId);
        if (r == null) {
            sysout.print("ACCOUNT NOT FOUND: " + acctId + "\n");
            sysout.print("ERROR READING XREF FILE\n");
            abend("0023");
        }
        xrefCard = Records.xrefCard(r);
    }

    private void getInterestRate(String group, String type, String cat) {
        String r = discgrp.get(group + type + cat);
        if (r == null) {
            sysout.print("DISCLOSURE GROUP RECORD MISSING\n");
            sysout.print("TRY WITH DEFAULT GROUP CODE\n");
            r = discgrp.get(RecordFile.pad("DEFAULT", 10) + type + cat);
            if (r == null) {
                sysout.print("ERROR READING DEFAULT DISCLOSURE GROUP\n");
                abend("0023");
            }
        }
        rate = Records.discRate(r);
    }

    private void computeInterest(BigDecimal bal) {
        // No ROUNDED in the COBOL: truncate toward zero (D2), then drop high-order digits.
        BigDecimal monthly = Zoned.truncate(
                bal.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 9, 2);
        totalInt = Zoned.truncate(totalInt.add(monthly), 9, 2);
        writeTx(monthly);
    }

    private void writeTx(BigDecimal monthly) {
        tranIdSuffix = (tranIdSuffix + 1) % 1_000_000;
        String id = parmDate + String.format("%06d", tranIdSuffix);
        String c = clock.get();
        String ts = c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
                + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14) + "."
                + c.substring(14, 16) + "0000";
        transact.add(Records.tran(id, Records.acctId(account), monthly, xrefCard, ts));
    }

    /** 1400-COMPUTE-FEES: empty in the COBOL (D3, CR-2). */
    private void computeFees() {
    }

    private void updateAccount() {
        account = Records.acctUpdated(account, totalInt);
        accounts.put(Records.acctId(account), account);
    }
}
