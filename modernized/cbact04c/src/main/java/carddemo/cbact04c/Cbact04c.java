package carddemo.cbact04c;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java 21 rewrite of CBACT04C (nightly interest calculation). Behaviour-preserving, defects included. */
public final class Cbact04c {

    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.");

    private static final class Abend extends RuntimeException {
        Abend() { super(null, null, false, false); }
    }

    private final String parm;
    private final Datasets dds;
    private final Clock clock;
    private final PrintStream out;

    private Cbact04c(String parm, Datasets dds, Clock clock, PrintStream out) {
        this.parm = Datasets.pad(parm == null ? "" : parm, 10);
        this.dds = dds;
        this.clock = clock;
        this.out = out;
    }

    public static void main(String[] args) {
        Clock clock = Clock.systemDefaultZone();
        String frozen = System.getenv("CARDDEMO_CURRENT_DATE");
        if (frozen != null && frozen.length() >= 16) {
            LocalDateTime t = LocalDateTime.parse(frozen.substring(0, 14), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    .withNano(Integer.parseInt(frozen.substring(14, 16)) * 10_000_000);
            clock = Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
        int rc = run(args.length > 0 ? args[0] : "", Datasets.fromEnv(), clock, System.out);
        System.out.flush();
        System.exit(rc);
    }

    public static int run(String parm, Datasets dds, Clock clock, PrintStream out) {
        return new Cbact04c(parm, dds, clock, out).execute();
    }

    private int execute() {
        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        TreeMap<String, String> accts = new TreeMap<>();
        List<String> trans = new ArrayList<>();
        try {
            List<String> tcat = open(Datasets.TCATBALF, Records.TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
            List<String> xrefRows = open(Datasets.XREFFILE, Records.XREF_LEN, "ERROR OPENING CROSS REF FILE");
            List<String> discRows = open(Datasets.DISCGRP, Records.DISC_LEN, "ERROR OPENING DALY REJECTS FILE");
            List<String> acctRows = open(Datasets.ACCTFILE, Records.ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE");
            if (!dds.canWrite(Datasets.TRANSACT)) {
                openError("ERROR OPENING TRANSACTION FILE");
            }
            for (String r : acctRows) {
                accts.put(Records.acctId(r), r);
            }
            Map<String, String> xref = new HashMap<>();
            for (String r : xrefRows) {
                xref.putIfAbsent(Records.xrefAcctId(r), r);
            }
            Map<String, String> disc = new HashMap<>();
            for (String r : discRows) {
                disc.put(Records.discKey(r), r);
            }
            process(tcat, accts, xref, disc, trans);
            out.println("END OF EXECUTION OF PROGRAM CBACT04C");
            return 0;
        } catch (Abend a) {
            out.println("ABENDING PROGRAM");
            System.err.println("CEE3ABD: ABEND U0999");
            return 16;
        } finally {
            if (dds.dd().containsKey(Datasets.TRANSACT) && java.nio.file.Files.isRegularFile(dds.dd().get(Datasets.TRANSACT))) {
                dds.write(Datasets.TRANSACT, trans);
            }
            if (!accts.isEmpty()) {
                dds.write(Datasets.ACCTFILE, new ArrayList<>(accts.values()));
            }
        }
    }

    private void process(List<String> tcat, TreeMap<String, String> accts, Map<String, String> xref,
                         Map<String, String> disc, List<String> trans) {
        String lastAcct = " ".repeat(11);
        boolean first = true;
        BigDecimal totalInt = BigDecimal.ZERO;
        int suffix = 0;
        String acct = null;
        String card = null;
        for (String rec : tcat) {
            out.println(rec);
            String acctId = Records.tcatAcctId(rec);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    accts.put(Records.acctId(acct), Records.acctUpdate(acct, totalInt));
                } else {
                    first = false;
                }
                totalInt = BigDecimal.ZERO;
                lastAcct = acctId;
                acct = accts.get(acctId);
                if (acct == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    ioError("ERROR READING ACCOUNT FILE");
                }
                String x = xref.get(acctId);
                if (x == null) {
                    out.println("ACCOUNT NOT FOUND: " + acctId);
                    ioError("ERROR READING XREF FILE");
                }
                card = Records.xrefCardNum(x);
            }
            String key = Records.acctGroupId(acct) + Records.tcatTypeCd(rec) + Records.tcatCd(rec);
            String d = disc.get(key);
            if (d == null) {
                out.println("DISCLOSURE GROUP RECORD MISSING");
                out.println("TRY WITH DEFAULT GROUP CODE");
                d = disc.get("DEFAULT   " + key.substring(10));
                if (d == null) {
                    ioError("ERROR READING DEFAULT DISCLOSURE GROUP");
                }
            }
            BigDecimal rate = Records.discRate(d);
            if (rate.signum() != 0) {
                BigDecimal monthly = Zoned.truncate(
                        Records.tcatBal(rec).multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN), 2, 11);
                totalInt = Zoned.truncate(totalInt.add(monthly), 2, 11);
                suffix = (suffix + 1) % 1_000_000;
                trans.add(Records.tran(parm, suffix, Records.acctId(acct), monthly, card, timestamp()));
                // 1400-COMPUTE-FEES is an empty paragraph in the legacy program (D2)
            }
        }
        // D1: the last account is never rewritten, as in the COBOL main loop
    }

    private String timestamp() {
        ZonedDateTime t = ZonedDateTime.now(clock);
        return TS.format(t) + String.format("%02d0000", t.getNano() / 10_000_000);
    }

    private List<String> open(String name, int lrecl, String message) {
        List<String> rows = dds.read(name, lrecl);
        if (rows == null) {
            openError(message);
        }
        return rows;
    }

    private void openError(String message) {
        out.println(message);
        out.println("FILE STATUS IS: NNNN0035");
        throw new Abend();
    }

    private void ioError(String message) {
        out.println(message);
        out.println("FILE STATUS IS: NNNN0023");
        throw new Abend();
    }
}
