package com.v2.carddemo.cbtrn02c;

import static com.v2.carddemo.cbtrn02c.Records.*;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Java 21 rewrite of CBTRN02C (daily transaction posting), like-for-like.
 *
 * <p>Legacy defects are preserved on purpose (see README): D1 overlimit formula counts payments as spend,
 * D2 only the last failure is reported, D3 account REWRITE failure ignored, D4 no atomicity on a TRANFILE
 * write abend, D5 TRANFILE opened OUTPUT, D6 wrong status on DALYREJS close error, D7 silent truncation,
 * D8 ACTIVE-STATUS unchecked / text date compare / input PROC-TS dropped, D9 INITIALIZE leaves the TCATBAL
 * FILLER from the previous READ.
 */
public final class Cbtrn02c {
    private final Clock clock;
    private final PrintStream out;
    private final PrintStream err;
    private final Function<String, Path> dd;

    private List<String> dalytran;
    private TreeMap<String, String> xref;
    private TreeMap<String, String> acct;
    private TreeMap<String, String> tcat;
    private final TreeMap<String, String> tran = new TreeMap<>();
    private final List<String> rejects = new ArrayList<>();
    private boolean tranOpen, rejsOpen, acctOpen, tcatOpen;
    /** FILLER of the last TCATBAL record read; INITIALIZE does not reset it (D9). */
    private String tcatFiller = " ".repeat(22);

    public Cbtrn02c(Clock clock, PrintStream out, PrintStream err, Function<String, Path> dd) {
        this.clock = clock;
        this.out = out;
        this.err = err;
        this.dd = dd;
    }

    /** Thrown to unwind to the abend handler. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    /** Runs the program and returns the return code (0, 4, or 16 on abend). */
    public int run() {
        int processed = 0, rejected = 0;
        try {
            out.println("START OF EXECUTION OF PROGRAM CBTRN02C");
            open();
            for (String in : dalytran) {
                processed = (processed + 1) % 1_000_000_000;
                int reason = 0;
                String desc = "";
                String xrefRec = xref.get(get(in, T_CARD, 16));
                String acctRec = null;
                if (xrefRec == null) {
                    reason = 100;
                    desc = "INVALID CARD NUMBER FOUND";
                } else {
                    acctRec = acct.get(get(xrefRec, X_ACCT, 11));
                    if (acctRec == null) {
                        reason = 101;
                        desc = "ACCOUNT RECORD NOT FOUND";
                    } else {
                        long amt = money(in, T_AMT, 11);
                        long temp = Zoned.truncate(money(acctRec, A_CYC_CREDIT, 12)
                                - money(acctRec, A_CYC_DEBIT, 12) + amt, 9 + 2);
                        if (!(money(acctRec, A_LIMIT, 12) >= temp)) {
                            reason = 102;
                            desc = "OVERLIMIT TRANSACTION";
                        }
                        if (!(get(acctRec, A_EXPIRY, 10).compareTo(get(in, T_ORIG_TS, 10)) >= 0)) {
                            reason = 103;
                            desc = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
                        }
                    }
                }
                if (reason != 0) {
                    rejected = (rejected + 1) % 1_000_000_000;
                    String trailer = String.format("%04d", reason) + pad(desc, 76);
                    rejects.add(in + trailer);
                } else {
                    post(in, get(xrefRec, X_ACCT, 11), acctRec);
                }
            }
            close();
            out.println("TRANSACTIONS PROCESSED :" + String.format("%09d", processed));
            out.println("TRANSACTIONS REJECTED  :" + String.format("%09d", rejected));
            out.println("END OF EXECUTION OF PROGRAM CBTRN02C");
            return rejected > 0 ? 4 : 0;
        } catch (Abend a) {
            flush();
            err.println("CEE3ABD: ABEND U0999 TIMING=0");
            return 16;
        }
    }

    private void open() {
        step("ERROR OPENING DALYTRAN", () -> dalytran = Datasets.loadSeq(dd.apply("DALYTRAN"), TRAN_LEN));
        step("ERROR OPENING TRANSACTION FILE", () -> {
            Datasets.write(dd.apply("TRANFILE"), List.of()); // OPEN OUTPUT creates/replaces (D5)
            tranOpen = true;
        });
        step("ERROR OPENING CROSS REF FILE", () -> xref = Datasets.loadKsds(dd.apply("XREFFILE"), XREF_LEN, 16));
        step("ERROR OPENING DALY REJECTS FILE", () -> {
            Datasets.write(dd.apply("DALYREJS"), List.of());
            rejsOpen = true;
        });
        step("ERROR OPENING ACCOUNT MASTER FILE", () -> {
            acct = Datasets.loadKsds(dd.apply("ACCTFILE"), ACCT_LEN, 11);
            acctOpen = true;
        });
        step("ERROR OPENING TRANSACTION BALANCE FILE", () -> {
            tcat = Datasets.loadKsds(dd.apply("TCATBALF"), TCAT_LEN, 17);
            tcatOpen = true;
        });
    }

    /** KSDS data is written back on close; DALYREJS and TRANFILE likewise. */
    private void close() {
        flush();
    }

    private void flush() {
        if (tranOpen) Datasets.write(dd.apply("TRANFILE"), tran.values());
        if (rejsOpen) Datasets.write(dd.apply("DALYREJS"), rejects);
        if (acctOpen) Datasets.write(dd.apply("ACCTFILE"), acct.values());
        if (tcatOpen) Datasets.write(dd.apply("TCATBALF"), tcat.values());
    }

    private void post(String in, String acctId, String acctRec) {
        long amt = money(in, T_AMT, 11);
        // 2000-POST-TRANSACTION: bytes 1-304 from input, PROC-TS from clock, FILLER spaces
        String rec = get(in, 1, 304) + procTs() + " ".repeat(20);

        // 2700-UPDATE-TCATBAL
        String key = acctId + get(in, T_TYPE, 2) + get(in, T_CAT, 4);
        String cat = tcat.get(key);
        if (cat == null) {
            out.println("TCATBAL record not found for key : " + key + ".. Creating.");
            cat = key + Zoned.encode(Zoned.truncate(amt, 11), 11) + tcatFiller;
        } else {
            tcatFiller = get(cat, C_FILLER, 22);
            cat = put(cat, C_BAL, Zoned.encode(Zoned.truncate(money(cat, C_BAL, 11) + amt, 11), 11));
        }
        tcat.put(key, cat);

        // 2800-UPDATE-ACCOUNT-REC (REWRITE failure ignored, D3)
        acctRec = put(acctRec, A_BAL, Zoned.encode(money(acctRec, A_BAL, 12) + amt, 12));
        int off = amt >= 0 ? A_CYC_CREDIT : A_CYC_DEBIT;
        acctRec = put(acctRec, off, Zoned.encode(money(acctRec, off, 12) + amt, 12));
        acct.put(acctId, acctRec);

        // 2900-WRITE-TRANSACTION-FILE (duplicate key aborts after balances were posted, D4)
        String id = get(rec, T_ID, 16);
        if (tran.containsKey(id)) {
            out.println("ERROR WRITING TO TRANSACTION FILE");
            showStatus("22");
            abend();
        }
        tran.put(id, rec);
    }

    private String procTs() {
        ZonedDateTime t = ZonedDateTime.now(clock);
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    private static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    private void step(String errorMsg, Runnable r) {
        try {
            r.run();
        } catch (FileStatusException e) {
            out.println(errorMsg);
            showStatus(e.status);
            abend();
        }
    }

    private void showStatus(String status) {
        out.println("FILE STATUS IS: NNNN00" + status);
    }

    private void abend() {
        out.println("ABENDING PROGRAM");
        throw new Abend();
    }
}
