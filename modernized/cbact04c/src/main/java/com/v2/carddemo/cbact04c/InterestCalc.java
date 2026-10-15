package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Java port of CBACT04C (INTCALC): monthly interest per account/category, byte-compatible with the COBOL. */
public final class InterestCalc {
    private static final int TCATBAL_LEN = 50;
    private static final int XREF_LEN = 50;
    private static final int ACCT_LEN = 300;
    private static final int DISC_LEN = 50;
    private static final int TRAN_LEN = 350;

    /** COBOL ABEND (CEE3ABD) after the error lines were displayed. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    private final String parm;
    private final String clock;
    private final PrintStream sysout;

    private final Map<String, String> accounts = new TreeMap<>();
    private final Map<String, String> cardByAcct = new HashMap<>();
    private final Map<String, String> rates = new HashMap<>();
    private final List<String> trans = new ArrayList<>();

    private int tranSuffix;
    private String acctRec;
    private String card;
    private BigDecimal totalInt = BigDecimal.ZERO.setScale(2);

    /**
     * @param parm   10-character run date used as TRAN-ID prefix
     * @param clock  {@code YYYYMMDDHHMMSSss}, as {@code FUNCTION CURRENT-DATE}
     * @param sysout receives the DISPLAY lines
     */
    public InterestCalc(String parm, String clock, PrintStream sysout) {
        this.parm = pad(parm, 10);
        this.clock = clock;
        this.sysout = sysout;
    }

    /** Runs the job; returns the return code (0, or 16 on abend). */
    public int run(Path in, Path out) throws IOException {
        sysout.print("START OF EXECUTION OF PROGRAM CBACT04C\n");
        List<String> tcatbal;
        try {
            tcatbal = open(in, "TCATBALF", TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
            for (String r : open(in, "XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE35")) {
                cardByAcct.putIfAbsent(r.substring(25, 36), r.substring(0, 16));
            }
            for (String r : open(in, "DISCGRP", DISC_LEN, "ERROR OPENING DALY REJECTS FILE")) {
                rates.put(r.substring(0, 16), r.substring(16, 22));
            }
            for (String r : open(in, "ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE")) {
                accounts.put(r.substring(0, 11), r);
            }
        } catch (Abend e) {
            return abendRc();
        }

        int rc = 0;
        try {
            process(tcatbal);
            sysout.print("END OF EXECUTION OF PROGRAM CBACT04C\n");
        } catch (Abend e) {
            rc = abendRc();
        }
        write(out.resolve("TRANSACT.txt"), trans);
        write(out.resolve("ACCTFILE.txt"), new ArrayList<>(accounts.values()));
        return rc;
    }

    private void process(List<String> tcatbal) {
        String lastAcct = "";
        boolean first = true;
        for (String rec : tcatbal) {
            sysout.print(rec + "\n");
            String acctId = rec.substring(0, 11);
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount();
                }
                first = false;
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acctRec = accounts.get(acctId);
                if (acctRec == null) {
                    sysout.print("ACCOUNT NOT FOUND: " + acctId + "\n");
                    fail("ERROR READING ACCOUNT FILE", "0023");
                }
                card = cardByAcct.get(acctId);
                if (card == null) {
                    sysout.print("ACCOUNT NOT FOUND: " + acctId + "\n");
                    fail("ERROR READING XREF FILE", "0023");
                }
            }
            String groupId = acctRec.substring(112, 122);
            String rateStr = rates.get(groupId + type + cat);
            if (rateStr == null) {
                sysout.print("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n");
                rateStr = rates.get("DEFAULT   " + type + cat);
                if (rateStr == null) {
                    fail("ERROR READING DEFAULT DISCLOSURE GROUP", "0023");
                }
            }
            BigDecimal rate = Zoned.decode(rateStr);
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28));
                BigDecimal monthly = Zoned.fit(bal.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 9);
                totalInt = Zoned.fit(totalInt.add(monthly), 9);
                writeTran(monthly);
            }
        }
    }

    private void writeTran(BigDecimal monthly) {
        tranSuffix = (tranSuffix + 1) % 1_000_000;
        String ts = clock.substring(0, 4) + "-" + clock.substring(4, 6) + "-" + clock.substring(6, 8) + "-"
                + clock.substring(8, 10) + "." + clock.substring(10, 12) + "." + clock.substring(12, 14) + "."
                + clock.substring(14, 16) + "0000";
        String rec = parm + String.format("%06d", tranSuffix)
                + "01" + "0005" + pad("System", 10)
                + pad("Int. for a/c " + acctRec.substring(0, 11), 100)
                + Zoned.encode(monthly, 11)
                + "000000000" + pad("", 50) + pad("", 50) + pad("", 10)
                + card + ts + ts + pad("", 20);
        trans.add(rec);
    }

    private void updateAccount() {
        BigDecimal bal = Zoned.fit(Zoned.decode(acctRec.substring(12, 24)).add(totalInt), 10);
        String zero = Zoned.encode(BigDecimal.ZERO, 12);
        acctRec = acctRec.substring(0, 12) + Zoned.encode(bal, 12) + acctRec.substring(24, 78)
                + zero + zero + acctRec.substring(102);
        accounts.put(acctRec.substring(0, 11), acctRec);
    }

    private List<String> open(Path in, String dd, int lrecl, String openError) throws IOException {
        Path p = in.resolve(dd + ".txt");
        if (!Files.isRegularFile(p)) {
            fail(openError, "0035");
        }
        List<String> recs = new ArrayList<>();
        String text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (!line.isEmpty()) {
                recs.add(pad(line, lrecl));
            }
        }
        return recs;
    }

    private void fail(String message, String status) {
        sysout.print(message + "\nFILE STATUS IS: NNNN" + status + "\n");
        throw new Abend();
    }

    private int abendRc() {
        sysout.print("ABENDING PROGRAM\n");
        System.err.println("CEE3ABD: ABEND U0999");
        return 16;
    }

    private static void write(Path p, List<String> recs) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : recs) {
            sb.append(r).append('\n');
        }
        Files.write(p, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String pad(String s, int len) {
        return s.length() >= len ? s : s + " ".repeat(len - s.length());
    }
}
