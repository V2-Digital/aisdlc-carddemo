package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Java 21 port of CBACT04C (interest calculation). Legacy defects are preserved on purpose, see README. */
public final class InterestBatch {
    static final int TCAT_LEN = 50, XREF_LEN = 50, DISC_LEN = 50, ACCT_LEN = 300, TRAN_LEN = 350;
    private static final BigDecimal DIVISOR = new BigDecimal("1200");

    private final Consumer<String> sysout;
    private final Clock clock;
    private final String parmDate;

    private final TreeMap<String, String> tcatbal = new TreeMap<>();
    private final TreeMap<String, String> xrefByCard = new TreeMap<>();
    private final Map<String, String> xrefByAcct = new HashMap<>();
    private final TreeMap<String, String> discgrp = new TreeMap<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();
    private final List<String> transactions = new ArrayList<>();
    private int tranSuffix = 0;

    public InterestBatch(String parm, Clock clock, Consumer<String> sysout) {
        this.parmDate = RecordFiles.pad(parm, 10).substring(0, 10);
        this.clock = clock;
        this.sysout = sysout;
    }

    public List<String> transactions() { return transactions; }

    public Iterable<String> accounts() { return accounts.values(); }

    /** Runs against files; writes TRANSACT.txt and ACCTFILE.txt in outDir. */
    public void run(Path tcatbalf, Path xreffile, Path acctfile, Path discgrpFile, Path outDir) throws IOException {
        sysout.accept("START OF EXECUTION OF PROGRAM CBACT04C");
        load(tcatbalf, TCAT_LEN, 17, tcatbal, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        load(xreffile, XREF_LEN, 16, xrefByCard, "ERROR OPENING CROSS REF FILE");
        load(discgrpFile, DISC_LEN, 16, discgrp, "ERROR OPENING DALY REJECTS FILE");
        load(acctfile, ACCT_LEN, 11, accounts, "ERROR OPENING ACCOUNT MASTER FILE");
        process();
        Files.createDirectories(outDir);
        RecordFiles.write(outDir.resolve("TRANSACT.txt"), transactions, TRAN_LEN);
        RecordFiles.write(outDir.resolve("ACCTFILE.txt"), accounts.values(), ACCT_LEN);
    }

    private void load(Path f, int lrecl, int keyLen, TreeMap<String, String> into, String openError) {
        try {
            for (String r : RecordFiles.read(f, lrecl)) into.put(r.substring(0, keyLen), r);
        } catch (IOException e) {
            sysout.accept(openError);
            sysout.accept("FILE STATUS IS: NNNN0035");
            throw abend();
        }
    }

    /** Loads in-memory datasets; used by tests. */
    public void process() {
        for (String card : xrefByCard.keySet()) {
            String r = xrefByCard.get(card);
            xrefByAcct.putIfAbsent(r.substring(25, 36), r); // D7: first card in key order wins
        }
        String lastAcct = "           ";
        boolean first = true;
        String account = null;
        BigDecimal total = BigDecimal.ZERO;
        String xref = null;
        for (String rec : tcatbal.values()) {
            sysout.accept(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(account, total);
                } else {
                    first = false;
                }
                total = BigDecimal.ZERO;
                lastAcct = acctId;
                account = accounts.get(acctId);
                if (account == null) fail("ACCOUNT NOT FOUND: " + acctId, "ERROR READING ACCOUNT FILE");
                xref = xrefByAcct.get(acctId);
                if (xref == null) fail("ACCOUNT NOT FOUND: " + acctId, "ERROR READING XREF FILE");
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            String rate = discgrp.get(account.substring(112, 122) + type + cat);
            if (rate == null) {
                sysout.accept("DISCLOSURE GROUP RECORD MISSING");
                sysout.accept("TRY WITH DEFAULT GROUP CODE");
                rate = discgrp.get(RecordFiles.pad("DEFAULT", 10) + type + cat);
                if (rate == null) fail("ERROR READING DEFAULT DISCLOSURE GROUP");
            }
            BigDecimal intRate = Overpunch.decode(rate.substring(16, 22), 2);
            if (intRate.signum() != 0) {
                BigDecimal bal = Overpunch.decode(rec.substring(17, 28), 2);
                // no ROUNDED in the COBOL: truncate to 2 dp (D2), then S9(9)V99 loses high-order digits (D6)
                BigDecimal monthly = Overpunch.wrap(
                        bal.multiply(intRate).divide(DIVISOR, 2, RoundingMode.DOWN), 11, 2);
                total = Overpunch.wrap(total.add(monthly), 11, 2);
                writeTransaction(acctId, monthly, xref.substring(0, 16));
                // 1400-COMPUTE-FEES is empty in the legacy program (D4)
            }
        }
        // D1: the last account held is never rewritten
        sysout.accept("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private void updateAccount(String account, BigDecimal total) {
        BigDecimal bal = Overpunch.decode(account.substring(12, 24), 2);
        String zero = Overpunch.encode(BigDecimal.ZERO, 12, 2);
        String out = account.substring(0, 12)
                + Overpunch.encode(bal.add(total), 12, 2)
                + account.substring(24, 78) + zero + zero + account.substring(102);
        accounts.put(account.substring(0, 11), out);
    }

    private void writeTransaction(String acctId, BigDecimal amount, String card) {
        tranSuffix = (tranSuffix + 1) % 1_000_000;
        String ts = Clock.db2Timestamp(clock.currentDate());
        String r = parmDate + String.format("%06d", tranSuffix)
                + "01" + "0005" + RecordFiles.pad("System", 10)
                + RecordFiles.pad("Int. for a/c " + acctId, 100)
                + Overpunch.encode(amount, 11, 2) + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card + ts + ts + " ".repeat(20);
        transactions.add(r);
    }

    private void fail(String... lines) {
        for (String l : lines) sysout.accept(l);
        sysout.accept("FILE STATUS IS: NNNN0023");
        throw abend();
    }

    private AbendException abend() {
        sysout.accept("ABENDING PROGRAM");
        return new AbendException("CEE3ABD: ABEND U0999");
    }
}
