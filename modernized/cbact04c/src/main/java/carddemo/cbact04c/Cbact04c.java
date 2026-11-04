package carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Like-for-like Java port of CBACT04C (interest calculation). Legacy defects D1-D5 are preserved. */
public final class Cbact04c {
    private static final BigDecimal DIVISOR = BigDecimal.valueOf(1200);
    private static final String ZERO_AMT = "00000000000{";

    /** Raised where the COBOL program performs 9999-ABEND-PROGRAM. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    private final String parm;
    private final Clock clock;
    private final PrintStream out;
    private final PrintStream err;

    private final Map<String, String> discgrp = new HashMap<>();
    private final Map<String, String> xref = new HashMap<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();
    private final List<String> transactions = new ArrayList<>();
    private int tranSuffix;

    public Cbact04c(String parm, Clock clock, PrintStream out, PrintStream err) {
        String p = parm.length() > 10 ? parm.substring(0, 10) : parm;
        this.parm = p + " ".repeat(10 - p.length());
        this.clock = clock;
        this.out = out;
        this.err = err;
    }

    /** Runs the job; returns the exit code (0, or 16 on abend). */
    public int run(Path inDir, Path outDir) throws IOException {
        out.print("START OF EXECUTION OF PROGRAM CBACT04C\n");
        try {
            List<String> tcatbal = open(inDir, "TCATBALF", 50, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
            for (String r : open(inDir, "XREFFILE", 50, "ERROR OPENING CROSS REF FILE")) {
                xref.putIfAbsent(r.substring(25, 36), r);
            }
            for (String r : open(inDir, "DISCGRP", 50, "ERROR OPENING DALY REJECTS FILE")) {
                discgrp.put(r.substring(0, 16), r);
            }
            for (String r : open(inDir, "ACCTFILE", 300, "ERROR OPENING ACCOUNT MASTER FILE")) {
                accounts.put(r.substring(0, 11), r);
            }
            tcatbal.sort((a, b) -> a.substring(0, 17).compareTo(b.substring(0, 17)));
            process(tcatbal);
        } catch (Abend a) {
            out.print("ABENDING PROGRAM\n");
            err.print("CEE3ABD: ABEND U0999\n");
            out.flush();
            return 16;
        }
        Files.createDirectories(outDir);
        FixedFile.write(outDir.resolve("TRANSACT.txt"), transactions);
        FixedFile.write(outDir.resolve("ACCTFILE.txt"), new ArrayList<>(accounts.values()));
        out.print("END OF EXECUTION OF PROGRAM CBACT04C\n");
        out.flush();
        return 0;
    }

    private List<String> open(Path dir, String dd, int lrecl, String errText) throws IOException {
        Path f = dir.resolve(dd + ".txt");
        if (!Files.exists(f)) {
            out.print(errText + (dd.equals("XREFFILE") ? "35" : "") + "\n");
            status("0035");
            throw new Abend();
        }
        return FixedFile.read(f, lrecl);
    }

    private void status(String s) {
        out.print("FILE STATUS IS: NNNN" + s + "\n");
    }

    private void process(List<String> tcatbal) {
        String lastAcct = null;
        String acct = null;
        String card = null;
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (String rec : tcatbal) {
            out.print(rec + "\n");
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (lastAcct != null) {
                    acct = update(acct, total);
                }
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    out.print("ACCOUNT NOT FOUND: " + acctId + "\n");
                    out.print("ERROR READING ACCOUNT FILE\n");
                    status("0023");
                    throw new Abend();
                }
                String x = xref.get(acctId);
                if (x == null) {
                    out.print("ACCOUNT NOT FOUND: " + acctId + "\n");
                    out.print("ERROR READING XREF FILE\n");
                    status("0023");
                    throw new Abend();
                }
                card = x.substring(0, 16);
            }
            BigDecimal rate = rate(acct.substring(112, 122), rec.substring(11, 17));
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28));
                BigDecimal monthly = Zoned.wrap(
                        bal.multiply(rate).divide(DIVISOR, 2, RoundingMode.DOWN), 9);
                total = Zoned.wrap(total.add(monthly), 9);
                writeTransaction(acctId, card, monthly);
                // 1400-COMPUTE-FEES: empty in the legacy program
            }
        }
        // D1: the last account is never updated; its record stays as read.
    }

    private BigDecimal rate(String group, String typeCat) {
        String r = discgrp.get(group + typeCat);
        if (r == null) {
            out.print("DISCLOSURE GROUP RECORD MISSING\n");
            out.print("TRY WITH DEFAULT GROUP CODE\n");
            r = discgrp.get("DEFAULT   " + typeCat);
            if (r == null) {
                out.print("ERROR READING DEFAULT DISCLOSURE GROUP\n");
                status("0023");
                throw new Abend();
            }
        }
        return Zoned.decode(r.substring(16, 22));
    }

    /** 1050-UPDATE-ACCOUNT: add interest, zero the cycle fields (D4), replace the record. */
    private String update(String acct, BigDecimal total) {
        BigDecimal bal = Zoned.decode(acct.substring(12, 24)).add(total);
        String rec = acct.substring(0, 12) + Zoned.encode(bal, 10)
                + acct.substring(24, 78)
                + ZERO_AMT + ZERO_AMT + acct.substring(102);
        accounts.put(acct.substring(0, 11), rec);
        return rec;
    }

    private void writeTransaction(String acctId, String card, BigDecimal monthly) {
        tranSuffix = (tranSuffix + 1) % 1_000_000;
        LocalDateTime t = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute(),
                t.getSecond(), t.getNano() / 10_000_000);
        String desc = "Int. for a/c " + acctId;
        transactions.add(parm + String.format("%06d", tranSuffix)
                + "01" + "0005" + "System    "
                + desc + " ".repeat(100 - desc.length())
                + Zoned.encode(monthly, 9)
                + "000000000"
                + " ".repeat(110)
                + card + ts + ts + " ".repeat(20));
    }
}
