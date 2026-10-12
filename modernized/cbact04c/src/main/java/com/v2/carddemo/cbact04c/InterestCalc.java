package com.v2.carddemo.cbact04c;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Line-for-line mirror of app/cbl/CBACT04C.cbl (INTCALC). Legacy defects are preserved on purpose:
 * D1 last account never rewritten, D2 truncation, D3 group lookup on cols 113-122, D4 silent overflow.
 */
public final class InterestCalc {
    /** Dataset locations; {@code acctfileOut} may equal {@code acctfile} (I-O semantics). */
    public record Files_(Path tcatbalf, Path xreffile, Path discgrp, Path acctfile, Path acctfileOut,
                         Path transact) {}

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private final Files_ files;
    private final String parmDate; // PIC X(10)
    private final Supplier<String> clock; // 16 digits YYYYMMDDHHMMSSss, FUNCTION CURRENT-DATE
    private final PrintStream out;

    public InterestCalc(Files_ files, String parm, Supplier<String> clock, PrintStream sysout) {
        this.files = files;
        this.parmDate = FixedText.pad(parm, 10);
        this.clock = clock;
        this.out = sysout;
    }

    private void display(String s) {
        out.print(s + "\n");
    }

    /** Runs the job; returns the exit status (0, or 16 on abend). Load errors propagate as Ksds.LoadError. */
    public int run() throws IOException {
        display("START OF EXECUTION OF PROGRAM CBACT04C");
        Ksds tcat = null, xref = null, disc = null, acct = null;
        OutputStream tran = null;
        try {
            // 0000..0400 open paragraphs, in order
            tcat = open("TCATBALF", files.tcatbalf(), 50, r -> r.substring(0, 17), null,
                    "ERROR OPENING TRANSACTION CATEGORY BALANCE");
            xref = open("XREFFILE", files.xreffile(), 50, r -> r.substring(0, 16), r -> r.substring(25, 36),
                    "ERROR OPENING CROSS REF FILE" + "35");
            disc = open("DISCGRP", files.discgrp(), 50, r -> r.substring(0, 16), null,
                    "ERROR OPENING DALY REJECTS FILE");
            acct = open("ACCTFILE", files.acctfile(), 300, r -> r.substring(0, 11), null,
                    "ERROR OPENING ACCOUNT MASTER FILE");
            try {
                tran = new BufferedOutputStream(Files.newOutputStream(files.transact()));
            } catch (IOException e) {
                abend("ERROR OPENING TRANSACTION FILE", "0030");
            }
            process(tcat, xref, disc, acct, tran);
            // 9000..9400 close paragraphs
            tran.close();
            tran = null;
            writeAccounts(acct);
            display("END OF EXECUTION OF PROGRAM CBACT04C");
            return 0;
        } catch (Abend a) {
            // VSAM semantics: rewritten accounts and written transactions stay as they were.
            if (tran != null) tran.close();
            if (acct != null) writeAccounts(acct);
            throw a;
        }
    }

    private Ksds open(String dd, Path p, int lrecl, java.util.function.Function<String, String> key,
                      java.util.function.Function<String, String> alt, String msg) throws IOException {
        try {
            return Ksds.load(dd, p, lrecl, key, alt);
        } catch (NoSuchFileException e) {
            return abend(msg, "0035");
        }
    }

    private <T> T abend(String msg, String status) {
        display(msg);
        display("FILE STATUS IS: NNNN" + status);
        display("ABENDING PROGRAM");
        throw new Abend();
    }

    private void process(Ksds tcat, Ksds xref, Ksds disc, Ksds acct, OutputStream tran) throws IOException {
        String lastAcct = " ".repeat(11);
        boolean firstTime = true;
        BigDecimal totalInt = ZERO;
        int suffix = 0;
        String acctRec = null;
        String xrefRec = null;
        String discRec = null;

        for (String rec : tcat.byKey.values()) { // 1000-TCATBALF-GET-NEXT
            display(rec); // :193
            String acctId = rec.substring(0, 11);
            BigDecimal catBal = decode(rec.substring(17, 28), "ERROR READING TRANSACTION CATEGORY FILE");
            if (!acctId.equals(lastAcct)) { // :194
                if (!firstTime) {
                    acctRec = updateAccount(acct, acctRec, totalInt); // :196
                } else {
                    firstTime = false;
                }
                totalInt = ZERO;
                lastAcct = acctId;
                acctRec = acct.byKey.get(acctId); // 1100-GET-ACCT-DATA
                if (acctRec == null) notFound(acctId, "ERROR READING ACCOUNT FILE");
                xrefRec = xref.byAlt.get(acctId); // 1110-GET-XREF-DATA
                if (xrefRec == null) notFound(acctId, "ERROR READING XREF FILE");
            }
            // 1200-GET-INTEREST-RATE
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            discRec = disc.byKey.get(acctRec.substring(112, 122) + type + cat);
            if (discRec == null) {
                display("DISCLOSURE GROUP RECORD MISSING");
                display("TRY WITH DEFAULT GROUP CODE");
                discRec = disc.byKey.get("DEFAULT   " + type + cat); // 1200-A
                if (discRec == null) {
                    display("ERROR READING DEFAULT DISCLOSURE GROUP");
                    display("FILE STATUS IS: NNNN0023");
                    display("ABENDING PROGRAM");
                    throw new Abend();
                }
            }
            BigDecimal rate = decode(discRec.substring(16, 22), "ERROR READING DISCLOSURE GROUP FILE");
            if (rate.signum() != 0) { // :214
                // 1300-COMPUTE-INTEREST: no ROUNDED (D2), no SIZE ERROR (D4)
                BigDecimal monthly = Zoned.wrap(
                        catBal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN), 9);
                totalInt = Zoned.wrap(totalInt.add(monthly), 9);
                suffix = (suffix + 1) % 1_000_000;
                writeTx(tran, suffix, acctId, xrefRec.substring(0, 16), monthly);
                // 1400-COMPUTE-FEES is empty (CR-2)
            }
        }
        // D1: the legacy ELSE PERFORM 1050-UPDATE-ACCOUNT (:220) is unreachable; last account not updated.
    }

    private <T> T notFound(String acctId, String msg) {
        display("ACCOUNT NOT FOUND: " + acctId);
        display(msg);
        display("FILE STATUS IS: NNNN0023");
        display("ABENDING PROGRAM");
        throw new Abend();
    }

    private BigDecimal decode(String field, String msg) {
        try {
            return Zoned.decode(field);
        } catch (NumberFormatException e) {
            return abend(msg, "0034"); // E9: Java-only guard for invalid data
        }
    }

    /** 1050-UPDATE-ACCOUNT: balance += total, cycle credit/debit = 0, REWRITE. Returns the new record. */
    private String updateAccount(Ksds acct, String rec, BigDecimal totalInt) {
        BigDecimal bal = decode(rec.substring(12, 24), "ERROR RE-WRITING ACCOUNT FILE");
        BigDecimal nb = Zoned.wrap(bal.add(totalInt), 10);
        String upd = rec.substring(0, 12) + Zoned.encode(nb, 12) + rec.substring(24, 78)
                + Zoned.encode(ZERO, 12) + Zoned.encode(ZERO, 12) + rec.substring(102);
        acct.byKey.put(rec.substring(0, 11), upd);
        return upd;
    }

    private void writeTx(OutputStream tran, int suffix, String acctId, String card, BigDecimal amt)
            throws IOException {
        String ts = timestamp();
        StringBuilder sb = new StringBuilder(350);
        sb.append(parmDate).append(String.format("%06d", suffix)); // TRAN-ID
        sb.append("01").append("0005"); // type, cat
        sb.append(FixedText.pad("System", 10));
        sb.append(FixedText.pad("Int. for a/c " + acctId, 100));
        sb.append(Zoned.encode(amt, 11));
        sb.append("000000000"); // merchant id
        sb.append(" ".repeat(110)); // merchant name, city, zip
        sb.append(card);
        sb.append(ts).append(ts);
        sb.append(" ".repeat(20)); // filler (spaces under the default dialect, F3)
        try {
            FixedText.write(tran, sb.toString());
        } catch (IOException e) {
            abend("ERROR WRITING TRANSACTION RECORD", "0030");
        }
    }

    /** Z-GET-DB2-FORMAT-TIMESTAMP: YYYY-MM-DD-HH.MM.SS.hh0000. */
    private String timestamp() {
        String c = clock.get();
        return c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
                + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14) + "."
                + c.substring(14, 16) + "0000";
    }

    /** Writes the account master (all records, key order) via temp file + rename. */
    private void writeAccounts(Ksds acct) throws IOException {
        Path dest = files.acctfileOut();
        Path dir = dest.toAbsolutePath().getParent();
        Path tmp = Files.createTempFile(dir, "acctfile", ".tmp");
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp))) {
            for (Map.Entry<String, String> e : acct.byKey.entrySet()) FixedText.write(os, e.getValue());
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }
}
