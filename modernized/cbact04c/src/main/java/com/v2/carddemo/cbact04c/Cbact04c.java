package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java port of CBACT04C (interest calculation), preserving legacy behaviour including defects. */
public final class Cbact04c {
    static final int TCATBAL_LEN = 50, XREF_LEN = 50, ACCT_LEN = 300, DISC_LEN = 50, TRAN_LEN = 350;
    private static final BigDecimal DIVISOR = BigDecimal.valueOf(1200);

    private final Clock clock;
    private final PrintStream sysout;

    public Cbact04c(Clock clock, PrintStream sysout) {
        this.clock = clock;
        this.sysout = sysout;
    }

    private void display(String s) {
        sysout.print(s + "\n");
    }

    /** Runs the job; returns the exit code (0, or 16 on abend). Outputs are written to {@code outDir}. */
    public int run(String parm, Path tcatbalf, Path xreffile, Path acctfile, Path discgrp, Path outDir)
            throws IOException {
        String parmDate = FixedFile.pad(parm, 10);
        display("START OF EXECUTION OF PROGRAM CBACT04C");

        List<String> tcatbal;
        TreeMap<String, String> xref = new TreeMap<>();
        TreeMap<String, String> disc = new TreeMap<>();
        TreeMap<String, String> accts = new TreeMap<>();
        List<String> transact = new ArrayList<>();
        try {
            tcatbal = open(tcatbalf, TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
            for (String r : open(xreffile, XREF_LEN, "ERROR OPENING CROSS REF FILE35")) {
                xref.put(r.substring(0, 16), r);
            }
            for (String r : open(discgrp, DISC_LEN, "ERROR OPENING DALY REJECTS FILE")) {
                disc.put(r.substring(0, 16), r);
            }
            for (String r : open(acctfile, ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE")) {
                accts.put(r.substring(0, 11), r);
            }
        } catch (AbendException e) {
            return AbendException.EXIT_CODE;
        }
        // alternate index on acct-id (offset 25, 11): first record in primary-key order wins
        Map<String, String> xrefByAcct = new TreeMap<>();
        for (String r : xref.values()) {
            xrefByAcct.putIfAbsent(r.substring(25, 36), r);
        }

        int rc = 0;
        try {
            process(parmDate, tcatbal, xrefByAcct, disc, accts, transact);
            display("END OF EXECUTION OF PROGRAM CBACT04C");
        } catch (AbendException e) {
            display(e.getMessage());
            rc = AbendException.EXIT_CODE;
        }
        Files.createDirectories(outDir);
        FixedFile.write(outDir.resolve("TRANSACT.txt"), transact);
        FixedFile.write(outDir.resolve("ACCTFILE.txt"), new ArrayList<>(accts.values()));
        return rc;
    }

    private List<String> open(Path file, int lrecl, String msg) throws IOException {
        if (!Files.isReadable(file)) {
            display(msg);
            display("FILE STATUS IS: NNNN0035");
            display("ABENDING PROGRAM");
            throw new AbendException();
        }
        return FixedFile.read(file, lrecl);
    }

    private void process(String parmDate, List<String> tcatbal, Map<String, String> xrefByAcct,
            TreeMap<String, String> disc, TreeMap<String, String> accts, List<String> transact) {
        String lastAcct = "";
        boolean first = true;
        BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
        int suffix = 0;
        String acct = null;     // ACCOUNT-RECORD in working storage
        String xrefRec = null;  // CARD-XREF-RECORD

        // D1: the legacy loop never runs the final update after EOF, so none is done here either.
        for (String rec : tcatbal) {
            display(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    acct = update(acct, totalInt, accts);
                } else {
                    first = false;
                }
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accts.get(acctId);
                if (acct == null) {
                    notFound(acctId, "ERROR READING ACCOUNT FILE");
                }
                xrefRec = xrefByAcct.get(acctId);
                if (xrefRec == null) {
                    notFound(acctId, "ERROR READING XREF FILE");
                }
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            String groupId = acct.substring(112, 122);
            String drec = disc.get(groupId + type + cat);
            if (drec == null) {
                display("DISCLOSURE GROUP RECORD MISSING");
                display("TRY WITH DEFAULT GROUP CODE");
                drec = disc.get(FixedFile.pad("DEFAULT", 10) + type + cat);
                if (drec == null) {
                    display("ERROR READING DEFAULT DISCLOSURE GROUP");
                    display("FILE STATUS IS: NNNN0023");
                    throw new AbendException();
                }
            }
            BigDecimal rate = ZonedDecimal.decode(drec.substring(16, 22), 2);
            if (rate.signum() != 0) {
                BigDecimal bal = ZonedDecimal.decode(rec.substring(17, 28), 2);
                BigDecimal monthly = ZonedDecimal.truncate(interest(bal, rate), 9, 2);
                totalInt = ZonedDecimal.truncate(totalInt.add(monthly), 9, 2);
                suffix = (suffix + 1) % 1_000_000;
                transact.add(transaction(parmDate, suffix, acct, monthly, xrefRec.substring(0, 16)));
                // 1400-COMPUTE-FEES: empty paragraph (D3)
            }
        }
    }

    /** {@code (bal * rate) / 1200}, truncated toward zero to 2 decimals (no ROUNDED in the COMPUTE). */
    public static BigDecimal interest(BigDecimal bal, BigDecimal rate) {
        return bal.multiply(rate).divide(DIVISOR, 2, RoundingMode.DOWN);
    }

    private void notFound(String acctId, String errMsg) {
        display("ACCOUNT NOT FOUND: " + acctId);
        display(errMsg);
        display("FILE STATUS IS: NNNN0023");
        throw new AbendException();
    }

    private String update(String acct, BigDecimal totalInt, TreeMap<String, String> accts) {
        BigDecimal bal = ZonedDecimal.decode(acct.substring(12, 24), 2);
        String newBal = ZonedDecimal.encode(bal.add(totalInt), 10, 2);
        String zero = ZonedDecimal.encode(BigDecimal.ZERO, 10, 2);
        String updated = acct.substring(0, 12) + newBal + acct.substring(24, 78) + zero + zero + acct.substring(102);
        accts.put(updated.substring(0, 11), updated);
        return updated;
    }

    private String transaction(String parmDate, int suffix, String acct, BigDecimal amt, String card) {
        String ts = timestamp();
        StringBuilder sb = new StringBuilder(TRAN_LEN);
        sb.append(parmDate).append(String.format("%06d", suffix));                   // 0/16 TRAN-ID
        sb.append("01").append("0005").append(FixedFile.pad("System", 10));          // type, cat, source
        sb.append(FixedFile.pad("Int. for a/c " + acct.substring(0, 11), 100));      // 32/100 desc
        sb.append(ZonedDecimal.encode(amt, 9, 2));                                   // 132/11 amount
        sb.append("000000000");                                                      // 143/9 merchant id
        sb.append(" ".repeat(110));                                                  // name, city, zip
        sb.append(card).append(ts).append(ts);                                       // 262, 278, 304
        sb.append(" ".repeat(20));                                                   // FILLER
        return sb.toString();
    }

    /** DB2 format timestamp {@code YYYY-MM-DD-HH.MM.SS.hh0000} from the clock. */
    private String timestamp() {
        ZonedDateTime t = ZonedDateTime.now(clock);
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }
}
