package carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Java 21 rewrite of CBACT04C (INTCALC), the nightly interest calculation.
 * Like-for-like: legacy defects (see README) are preserved on purpose.
 */
public final class Cbact04c {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int DISC_LEN = 50;
    static final int TRAN_LEN = 350;

    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    /** Fatal condition: the legacy DISPLAYs plus CEE3ABD. */
    private static final class Abend extends Exception {
        Abend() {
            super(null, null, false, false);
        }
    }

    private final Path dir;
    private final String parm;
    private final Supplier<String> clock;
    private final PrintStream out;

    private Cbact04c(Path dir, String parm, Supplier<String> clock, PrintStream out) {
        this.dir = dir;
        this.parm = (parm + " ".repeat(10)).substring(0, 10);
        this.clock = clock;
        this.out = out;
    }

    /** Runs the job on {@code DATA_DIR/<DD>.txt} and returns the return code (0 or 16). */
    public static int run(Path dataDir, String parm, Supplier<String> clock16, PrintStream sysout) {
        try {
            return new Cbact04c(dataDir, parm, clock16, sysout).execute();
        } catch (Abend a) {
            sysout.println("ABENDING PROGRAM");
            System.err.println("CEE3ABD: ABEND U0999");
            return 16;
        }
    }

    private int execute() throws Abend {
        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        TreeMap<String, String> tcatbal = load("TCATBALF", TCATBAL_LEN, 17, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        TreeMap<String, String> xref = load("XREFFILE", XREF_LEN, 16, "ERROR OPENING CROSS REF FILE");
        TreeMap<String, String> disc = load("DISCGRP", DISC_LEN, 16, "ERROR OPENING DALY REJECTS FILE");
        TreeMap<String, String> accts = load("ACCTFILE", ACCT_LEN, 11, "ERROR OPENING ACCOUNT MASTER FILE");

        // alternate index on XREF: account id -> record; first in primary-key order wins
        Map<String, String> xrefByAcct = new TreeMap<>();
        for (String rec : xref.values()) {
            xrefByAcct.putIfAbsent(rec.substring(25, 36), rec);
        }

        List<String> trans = new ArrayList<>();
        String lastAcct = " ".repeat(11);
        boolean first = true;
        String acct = null;     // current ACCOUNT-RECORD
        String card = null;     // current XREF-CARD-NUM
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        int suffix = 0;

        for (String rec : tcatbal.values()) {
            out.println(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    accts.put(acct.substring(0, 11), updateAccount(acct, total));
                } else {
                    first = false;
                }
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accts.get(acctId);
                if (acct == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    out.println("ERROR READING ACCOUNT FILE");
                    throw status(23);
                }
                String x = xrefByAcct.get(acctId);
                if (x == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    out.println("ERROR READING XREF FILE");
                    throw status(23);
                }
                card = x.substring(0, 16);
            }

            String tail = rec.substring(11, 17);  // type + category
            String hit = disc.get(acct.substring(112, 122) + tail);
            if (hit == null) {
                out.println("DISCLOSURE GROUP RECORD MISSING");
                out.println("TRY WITH DEFAULT GROUP CODE");
                hit = disc.get("DEFAULT   " + tail);
                if (hit == null) {
                    out.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                    throw status(23);
                }
            }
            BigDecimal rate = Zoned.parse(hit.substring(16, 22));
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.parse(rec.substring(17, 28));
                BigDecimal monthly = Zoned.fit(
                        bal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN), 11);
                total = Zoned.fit(total.add(monthly), 11);
                suffix = (suffix + 1) % 1_000_000;
                trans.add(transaction(suffix, acct.substring(0, 11), monthly, card));
                // 1400-COMPUTE-FEES: not implemented in the legacy program (CR-2)
            }
        }
        // D1: the last account is never rewritten (legacy loop never reaches 1050-UPDATE-ACCOUNT)

        try {
            FixedFile.write(dir.resolve("TRANSACT.txt"), trans);
            FixedFile.write(dir.resolve("ACCTFILE.txt"), accts.values());
        } catch (IOException e) {
            out.println("ERROR WRITING TRANSACTION RECORD");
            throw status(37);
        }
        out.println("END OF EXECUTION OF PROGRAM CBACT04C");
        return 0;
    }

    private TreeMap<String, String> load(String dd, int lrecl, int keyLen, String openError) throws Abend {
        Path file = dir.resolve(dd + ".txt");
        int status = Files.exists(file) ? 0 : 35;
        List<String> recs = null;
        if (status == 0) {
            try {
                recs = FixedFile.read(file, lrecl);
            } catch (IOException e) {
                status = 39;
            }
        }
        if (status != 0) {
            // the legacy program appends the status to this message only for XREFFILE
            out.println(dd.equals("XREFFILE") ? openError + String.format("%02d", status) : openError);
            throw status(status);
        }
        TreeMap<String, String> map = new TreeMap<>();
        for (String r : recs) {
            map.put(r.substring(0, keyLen), r);
        }
        return map;
    }

    /** 9910-DISPLAY-IO-STATUS, then the caller throws to abend. */
    private Abend status(int st) {
        out.println("FILE STATUS IS: NNNN" + String.format("%04d", st));
        return new Abend();
    }

    /** 1050-UPDATE-ACCOUNT: only balance and cycle credit/debit are touched. */
    private static String updateAccount(String acct, BigDecimal total) {
        BigDecimal bal = Zoned.parse(acct.substring(12, 24)).add(total);
        String zero = Zoned.format(BigDecimal.ZERO, 12);
        return acct.substring(0, 12) + Zoned.format(bal, 12) + acct.substring(24, 78)
                + zero + zero + acct.substring(102);
    }

    /** 1300-B-WRITE-TX. */
    private String transaction(int suffix, String acctId, BigDecimal amt, String card) {
        String ts = timestamp(clock.get());
        String desc = "Int. for a/c " + acctId;
        return parm + String.format("%06d", suffix)
                + "01" + "0005" + "System    "
                + desc + " ".repeat(100 - desc.length())
                + Zoned.format(amt, 11)
                + "000000000"
                + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card + ts + ts + " ".repeat(20);
    }

    /** CURRENT-DATE (YYYYMMDDHHMMSSss) to YYYY-MM-DD-HH.MM.SS.hh0000. */
    static String timestamp(String c) {
        return c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
                + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14)
                + "." + c.substring(14, 16) + "0000";
    }

    static String systemClock16() {
        String env = System.getenv("CARDDEMO_CURRENT_DATE");
        if (env != null && !env.isBlank()) {
            return env;
        }
        ZonedDateTime n = ZonedDateTime.now(ZoneOffset.UTC);
        return String.format("%04d%02d%02d%02d%02d%02d%02d", n.getYear(), n.getMonthValue(),
                n.getDayOfMonth(), n.getHour(), n.getMinute(), n.getSecond(), n.getNano() / 10_000_000);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: cbact04c <PARM> [DATA_DIR]");
            System.exit(2);
        }
        Path dir = Path.of(args.length > 1 ? args[1] : ".");
        System.exit(run(dir, args[0], Cbact04c::systemClock16, System.out));
    }
}
