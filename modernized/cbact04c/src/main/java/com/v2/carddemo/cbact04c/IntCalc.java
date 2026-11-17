package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Java rewrite of CBACT04C (interest calculation). Mirrors the legacy behaviour, defects included. */
public final class IntCalc {
    static final int TCATBAL_LEN = 50, XREF_LEN = 50, DISC_LEN = 50, ACCT_LEN = 300;

    private final String parmDate;
    private final String timestamp;
    private final Consumer<String> sysout;

    /**
     * @param parm  JCL PARM; the first 10 chars are the transaction-id date
     * @param now   16-digit {@code YYYYMMDDHHMMSSss}
     * @param sysout receives each DISPLAY line
     */
    public IntCalc(String parm, String now, Consumer<String> sysout) {
        this.parmDate = pad(parm.length() > 10 ? parm.substring(0, 10) : parm, 10);
        this.timestamp = now.substring(0, 4) + "-" + now.substring(4, 6) + "-" + now.substring(6, 8) + "-"
                + now.substring(8, 10) + "." + now.substring(10, 12) + "." + now.substring(12, 14) + "."
                + now.substring(14, 16) + "0000";
        this.sysout = sysout;
    }

    /** Runs the job. Returns nothing; writes TRANSACT.txt and ACCTFILE.txt to {@code out}. Throws {@link Abend}. */
    public void run(Path in, Path out) throws IOException {
        sysout.accept("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcatbal = open(in, "TCATBALF", TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xref = open(in, "XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE");
        List<String> disc = open(in, "DISCGRP", DISC_LEN, "ERROR OPENING DALY REJECTS FILE");
        List<String> acctRecs = open(in, "ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE");

        TreeMap<String, String> accounts = new TreeMap<>();
        acctRecs.forEach(r -> accounts.put(r.substring(0, 11), r));
        TreeMap<String, String> discgrp = new TreeMap<>();
        disc.forEach(r -> discgrp.put(r.substring(0, 16), r));
        // alternate key lookup: first match in primary-key (card number) order
        TreeMap<String, String> xrefByCard = new TreeMap<>();
        xref.forEach(r -> xrefByCard.put(r.substring(0, 16), r));

        List<String> transact = new ArrayList<>();
        String lastAcct = null;
        String acct = null;
        String card = null;
        BigDecimal total = BigDecimal.ZERO;
        int counter = 0;

        for (String rec : tcatbal) {
            sysout.accept(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (lastAcct != null) {
                    updateAccount(accounts, acct, total);
                }
                total = BigDecimal.ZERO;
                lastAcct = acctId;
                acct = acctId;
                if (!accounts.containsKey(acctId)) {
                    notFound(acctId, "ERROR READING ACCOUNT FILE");
                }
                card = null;
                for (String x : xrefByCard.values()) {
                    if (x.substring(25, 36).equals(acctId)) {
                        card = x.substring(0, 16);
                        break;
                    }
                }
                if (card == null) {
                    notFound(acctId, "ERROR READING XREF FILE");
                }
            }
            String groupId = accounts.get(acctId).substring(112, 122);
            String typeCat = rec.substring(11, 13) + rec.substring(13, 17);
            String dis = discgrp.get(groupId + typeCat);
            if (dis == null) {
                sysout.accept("DISCLOSURE GROUP RECORD MISSING");
                sysout.accept("TRY WITH DEFAULT GROUP CODE");
                dis = discgrp.get(pad("DEFAULT", 10) + typeCat);
                if (dis == null) {
                    sysout.accept("ERROR READING DEFAULT DISCLOSURE GROUP");
                    sysout.accept("FILE STATUS IS: NNNN0023");
                    abend();
                }
            }
            BigDecimal rate = Zoned.decode(dis.substring(16, 22));
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28));
                BigDecimal interest = Zoned.wrap(
                        bal.multiply(rate).divide(BigDecimal.valueOf(1200), 10, RoundingMode.DOWN)
                                .setScale(2, RoundingMode.DOWN), 9);
                total = Zoned.wrap(total.add(interest), 9);
                counter++;
                transact.add(transaction(counter, acctId, interest, card));
            }
        }
        // D1: the last account is deliberately not rewritten (legacy loop exits before 1050).

        write(out.resolve("TRANSACT.txt"), transact);
        write(out.resolve("ACCTFILE.txt"), new ArrayList<>(accounts.values()));
        sysout.accept("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private String transaction(int counter, String acctId, BigDecimal amt, String card) {
        return parmDate + String.format("%06d", counter % 1_000_000)
                + "01" + "0005" + pad("System", 10)
                + pad("Int. for a/c " + acctId, 100)
                + Zoned.encode(amt, 11)
                + "000000000" + " ".repeat(50) + " ".repeat(50) + " ".repeat(10)
                + card + timestamp + timestamp + " ".repeat(20);
    }

    /** Paragraph 1050-UPDATE-ACCOUNT. */
    private static void updateAccount(TreeMap<String, String> accounts, String id, BigDecimal total) {
        String r = accounts.get(id);
        BigDecimal bal = Zoned.decode(r.substring(12, 24));
        String zero = Zoned.encode(BigDecimal.ZERO, 12);
        // cyc-credit @79..90 and cyc-debit @91..102 (1-based) are both zeroed
        String nr = r.substring(0, 12) + Zoned.encode(Zoned.wrap(bal.add(total), 10), 12)
                + r.substring(24, 78) + zero + zero + r.substring(102);
        accounts.put(id, nr);
    }

    private List<String> open(Path in, String dd, int lrecl, String message) throws IOException {
        Path p = in.resolve(dd + ".txt");
        if (!Files.exists(p)) {
            sysout.accept(message);
            sysout.accept("FILE STATUS IS: NNNN0035");
            abend();
        }
        List<String> recs = new ArrayList<>();
        String text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
        for (String line : text.split("\n", -1)) {
            line = line.replace("\r", "");
            if (line.isEmpty()) {
                continue;
            }
            recs.add(line.length() >= lrecl ? line.substring(0, lrecl) : pad(line, lrecl));
        }
        return recs;
    }

    private void notFound(String id, String message) {
        sysout.accept("ACCOUNT NOT FOUND: " + id);
        sysout.accept(message);
        sysout.accept("FILE STATUS IS: NNNN0023");
        abend();
    }

    private void abend() {
        sysout.accept("ABENDING PROGRAM");
        throw new Abend();
    }

    private static void write(Path p, List<String> recs) throws IOException {
        StringBuilder sb = new StringBuilder();
        recs.forEach(r -> sb.append(r).append('\n'));
        Files.write(p, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    static String pad(String s, int n) {
        return s.length() >= n ? s : s + " ".repeat(n - s.length());
    }
}
