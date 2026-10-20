package com.v2.carddemo.cbtrn02c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Daily transaction posting (POSTTRAN / CBTRN02C), behaviour-preserving port.
 * Legacy defects are kept on purpose; see aisdlc intent.md #1-#6.
 */
public final class Cbtrn02c {

    static final int RC_OK = 0;
    static final int RC_REJECTS = 4;
    static final int RC_ABEND = 16;

    private static final int TRAN_LEN = 350;
    private static final int XREF_LEN = 50;
    private static final int ACCT_LEN = 300;
    private static final int TCAT_LEN = 50;

    /** Unrecoverable file error; the COBOL calls CEE3ABD (U0999). */
    static final class AbendException extends RuntimeException {
        AbendException(String message) {
            super(message);
        }
    }

    private final Map<String, String> env;
    private final Clock clock;
    private final PrintStream out;
    private final PrintStream err;

    private final TreeMap<String, String> tranFile = new TreeMap<>();
    private final TreeMap<String, String> accounts = new TreeMap<>();
    private final TreeMap<String, String> tcatbal = new TreeMap<>();
    private final TreeMap<String, String> xref = new TreeMap<>();
    private final List<String> rejects = new ArrayList<>();
    /** FILLER of the TCATBAL working record: INITIALIZE leaves it as the last successful READ left it. */
    private String tcatFiller = " ".repeat(22);

    Cbtrn02c(Map<String, String> env, Clock clock, PrintStream out, PrintStream err) {
        this.env = env;
        this.clock = clock;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        System.exit(new Cbtrn02c(env, clockFrom(env), System.out, System.err).run());
    }

    /** CARDDEMO_CURRENT_DATE, else COB_CURRENT_DATE (YYYYMMDDHHMMSSss), else system UTC. */
    static Clock clockFrom(Map<String, String> env) {
        String s = env.getOrDefault("CARDDEMO_CURRENT_DATE", env.get("COB_CURRENT_DATE"));
        if (s == null || s.length() < 16) {
            return Clock.systemUTC();
        }
        LocalDateTime t = LocalDateTime.of(
            Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
            Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
            Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
            Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    /** Runs the job and returns the process return code. */
    int run() {
        out.println("START OF EXECUTION OF PROGRAM CBTRN02C");
        try {
            List<String> dalytran = load("DALYTRAN", TRAN_LEN, "ERROR OPENING DALYTRAN");
            for (String l : load("XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE")) {
                xref.putIfAbsent(l.substring(0, 16), l);
            }
            for (String l : load("ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE")) {
                accounts.put(l.substring(0, 11), l);
            }
            for (String l : load("TCATBALF", TCAT_LEN, "ERROR OPENING TRANSACTION BALANCE FILE")) {
                tcatbal.put(l.substring(0, 17), l);
            }
            // TRANFILE and DALYREJS are OUTPUT: they must be writable, not readable.
            int processed = 0;
            int rejected = 0;
            for (String rec : dalytran) {
                processed++;
                int reason = 0;
                String desc = "";
                String card = rec.substring(262, 278);
                String xr = xref.get(card);
                String acctId = null;
                String acct = null;
                if (xr == null) {
                    reason = 100;
                    desc = "INVALID CARD NUMBER FOUND";
                } else {
                    acctId = xr.substring(25, 36);
                    acct = accounts.get(acctId);
                    if (acct == null) {
                        reason = 101;
                        desc = "ACCOUNT RECORD NOT FOUND";
                    }
                }
                if (acct != null) {
                    long amt = Zoned.decode(rec.substring(132, 143));
                    long credit = Zoned.decode(acct.substring(78, 90));
                    long debit = Zoned.decode(acct.substring(90, 102));
                    long limit = Zoned.decode(acct.substring(24, 36));
                    long temp = Zoned.truncate(credit - debit + amt, 11); // D1, D6
                    if (limit < temp) {
                        reason = 102;
                        desc = "OVERLIMIT TRANSACTION";
                    }
                    String expiry = acct.substring(58, 68);
                    if (expiry.compareTo(rec.substring(278, 288)) < 0) { // D2: overwrites 102
                        reason = 103;
                        desc = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
                    }
                }
                if (reason != 0) {
                    rejected++;
                    rejects.add(rec + String.format("%04d", reason) + Datasets.fit(desc, 76));
                } else {
                    post(rec, acctId);
                }
            }
            unloadAll();
            out.println(String.format("TRANSACTIONS PROCESSED :%09d", processed));
            out.println(String.format("TRANSACTIONS REJECTED  :%09d", rejected));
            out.println("END OF EXECUTION OF PROGRAM CBTRN02C");
            return rejected > 0 ? RC_REJECTS : RC_OK;
        } catch (AbendException e) {
            out.println(e.getMessage());
            out.println("ABENDING PROGRAM");
            err.println("CEE3ABD: ABEND U0999");
            return RC_ABEND;
        }
    }

    private void post(String rec, String acctId) {
        long amt = Zoned.decode(rec.substring(132, 143));

        // 2700-UPDATE-TCATBAL
        String key = acctId + rec.substring(16, 18) + rec.substring(18, 22);
        String tc = tcatbal.get(key);
        if (tc == null) {
            out.println("TCATBAL record not found for key : " + key + ".. Creating.");
            tc = key + "00000000000" + tcatFiller;
        } else {
            tcatFiller = tc.substring(28);
        }
        long bal = Zoned.decode(tc.substring(17, 28));
        tc = tc.substring(0, 17) + Zoned.encode(bal + amt, 11) + tc.substring(28);
        tcatbal.put(key, tc);

        // 2800-UPDATE-ACCOUNT-REC
        String a = accounts.get(acctId);
        long curr = Zoned.decode(a.substring(12, 24));
        String currNew = Zoned.encode(curr + amt, 12);
        if (amt >= 0) {
            long cc = Zoned.decode(a.substring(78, 90));
            a = a.substring(0, 12) + currNew + a.substring(24, 78)
                + Zoned.encode(cc + amt, 12) + a.substring(90);
        } else {
            long cd = Zoned.decode(a.substring(90, 102));
            a = a.substring(0, 12) + currNew + a.substring(24, 90)
                + Zoned.encode(cd + amt, 12) + a.substring(102);
        }
        accounts.put(acctId, a);

        // 2900-WRITE-TRANSACTION-FILE (DALYTRAN-PROC-TS is discarded)
        String tran = rec.substring(0, 304) + procTs() + " ".repeat(20);
        if (tranFile.putIfAbsent(tran.substring(0, 16), tran) != null) {
            throw new AbendException("ERROR WRITING TO TRANSACTION FILE\nFILE STATUS IS: NNNN0022");
        }
    }

    /** YYYY-MM-DD-HH.MM.SS.hh0000 */
    private String procTs() {
        Instant now = clock.instant();
        LocalDateTime t = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
            t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute(),
            t.getSecond(), t.getNano() / 10_000_000);
    }

    private List<String> load(String dd, int lrecl, String openError) {
        Path p = Datasets.ddPath(env, dd);
        try {
            if (p == null) {
                throw new NoSuchFileException(dd);
            }
            return Datasets.load(p, lrecl);
        } catch (IOException e) {
            throw new AbendException(openError + "\nFILE STATUS IS: NNNN0035");
        }
    }

    private void unloadAll() {
        try {
            unload("TRANFILE", tranFile.values(), "ERROR CLOSING TRANSACTION FILE");
            unload("DALYREJS", rejects, "ERROR CLOSING DAILY REJECTS FILE");
            unload("ACCTFILE", accounts.values(), "ERROR CLOSING ACCOUNT FILE");
            unload("TCATBALF", tcatbal.values(), "ERROR CLOSING TRANSACTION BALANCE FILE");
        } catch (IOException e) {
            throw new AbendException("ERROR CLOSING FILE\nFILE STATUS IS: NNNN0035");
        }
    }

    private void unload(String dd, Iterable<String> recs, String closeError) throws IOException {
        Path p = Datasets.ddPath(env, dd);
        if (p == null) {
            // D5: the legacy DALYREJS close error reports the XREFFILE status.
            throw new AbendException(closeError + "\nFILE STATUS IS: NNNN0035");
        }
        Datasets.unload(p, recs);
    }
}
