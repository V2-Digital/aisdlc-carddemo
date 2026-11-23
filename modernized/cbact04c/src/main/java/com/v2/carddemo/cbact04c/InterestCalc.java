package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Like-for-like port of CBACT04C (INTCALC), defects D1-D7 preserved. */
public final class InterestCalc {
    static final int TCATBAL_LRECL = 50;
    static final int XREF_LRECL = 50;
    static final int ACCT_LRECL = 300;
    static final int DISCGRP_LRECL = 50;
    static final int TRAN_LRECL = 350;

    private static final BigDecimal MONTHS_X100 = BigDecimal.valueOf(1200);

    private final Clock clock;
    private final PrintStream out;

    public InterestCalc(Clock clock, PrintStream out) {
        this.clock = clock;
        this.out = out;
    }

    /** Files bound to the JCL DD names. */
    public record Files(Path tcatbalf, Path xreffile, Path acctfile, Path discgrp,
                        Path transact, Path acctfileOut) {
    }

    /** Runs the job; throws AbendException on failure (outputs are then not written). */
    public void run(String parm, Files f) throws IOException {
        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        String parmDate = FixedWidthFiles.pad(parm, 10);

        List<String> tcatbal = FixedWidthFiles.read(f.tcatbalf(), TCATBAL_LRECL);
        // XREF alternate key (acct id) -> cards in file order
        Map<String, List<String>> xref = new HashMap<>();
        for (String r : FixedWidthFiles.read(f.xreffile(), XREF_LRECL)) {
            xref.computeIfAbsent(r.substring(25, 36), k -> new ArrayList<>()).add(r);
        }
        Map<String, String> disc = new HashMap<>();
        for (String r : FixedWidthFiles.read(f.discgrp(), DISCGRP_LRECL)) {
            disc.put(r.substring(0, 16), r);
        }
        Map<String, String> accounts = new TreeMap<>();
        for (String r : FixedWidthFiles.read(f.acctfile(), ACCT_LRECL)) {
            accounts.put(r.substring(0, 11), r);
        }

        List<String> trans = new ArrayList<>();
        String lastAcct = FixedWidthFiles.pad("", 11);
        boolean first = true;
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        int suffix = 0;
        String acct = null;
        String card = null;

        // D1: the legacy loop never reaches its post-EOF 1050-UPDATE-ACCOUNT, so the last account is not updated.
        for (String rec : tcatbal) {
            out.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    acct = updateAccount(acct, total, accounts);
                }
                first = false;
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    abend("ERROR READING ACCOUNT FILE", "0023");
                }
                List<String> cards = xref.get(acctId);
                if (cards == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    abend("ERROR READING XREF FILE", "0023");
                }
                // D7: a duplicate alt-key read returns status 02, which the legacy code treats as an error
                if (cards.size() > 1) {
                    abend("ERROR READING XREF FILE", "0002");
                }
                card = cards.getFirst().substring(0, 16);
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            String groupId = acct.substring(112, 122);
            String key = groupId + type + cat;
            String dg = disc.get(key);
            if (dg == null) {
                out.println("DISCLOSURE GROUP RECORD MISSING");
                out.println("TRY WITH DEFAULT GROUP CODE");
                dg = disc.get(FixedWidthFiles.pad("DEFAULT", 10) + type + cat);
                if (dg == null) {
                    abend("ERROR READING DEFAULT DISCLOSURE GROUP", "0023");
                }
            }
            BigDecimal rate = ZonedDecimal.parse(dg.substring(16, 22), 2);
            if (rate.signum() != 0) {
                BigDecimal bal = ZonedDecimal.parse(rec.substring(17, 28), 2);
                BigDecimal monthly = ZonedDecimal.truncateHighOrder(
                        bal.multiply(rate).divide(MONTHS_X100, 2, RoundingMode.DOWN), 9);
                total = ZonedDecimal.truncateHighOrder(total.add(monthly), 9);
                suffix = (suffix + 1) % 1_000_000;
                trans.add(tranRecord(parmDate, suffix, acct.substring(0, 11), monthly, card));
            }
        }

        FixedWidthFiles.write(f.transact(), trans);
        FixedWidthFiles.write(f.acctfileOut(), new ArrayList<>(accounts.values()));
        out.println("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    /** 1050-UPDATE-ACCOUNT: add interest, zero cycle credit/debit, rewrite. */
    private String updateAccount(String acct, BigDecimal total, Map<String, String> accounts) {
        BigDecimal bal = ZonedDecimal.parse(acct.substring(12, 24), 2);
        BigDecimal newBal = bal.add(total);
        String zero = ZonedDecimal.format(BigDecimal.ZERO, 10, 2);
        String updated = acct.substring(0, 12)
                + ZonedDecimal.format(newBal, 10, 2)
                + acct.substring(24, 78)
                + zero + zero
                + acct.substring(102);
        accounts.put(updated.substring(0, 11), updated);
        return updated;
    }

    private String tranRecord(String parmDate, int suffix, String acctId, BigDecimal amt, String card) {
        String ts = timestamp();
        return parmDate + String.format("%06d", suffix)
                + "01" + "0005"
                + FixedWidthFiles.pad("System", 10)
                + FixedWidthFiles.pad("Int. for a/c " + acctId, 100)
                + ZonedDecimal.format(amt, 9, 2)
                + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card + ts + ts
                + " ".repeat(20);
    }

    /** DB2 format timestamp YYYY-MM-DD-HH.MM.SS.hh0000 from the clock. */
    private String timestamp() {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    private void abend(String message, String status) {
        out.println(message);
        out.println("FILE STATUS IS: NNNN" + status);
        throw new AbendException(message);
    }
}
