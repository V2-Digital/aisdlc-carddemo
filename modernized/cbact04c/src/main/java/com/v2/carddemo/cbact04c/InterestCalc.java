package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** CBACT04C: monthly interest per account from transaction category balances. */
public final class InterestCalc {
    private static final BigDecimal DIV = BigDecimal.valueOf(1200);

    /** Input datasets by DD name, output paths and the DISPLAY sink. */
    private final Map<String, Path> in;
    private final String parm;
    private final Clock clock;
    private final Consumer<String> sysout;

    private final List<String> transact = new ArrayList<>();
    private final Map<String, String> accounts = new LinkedHashMap<>();
    private final Map<String, String> xref = new HashMap<>();
    private final Map<String, String> discgrp = new HashMap<>();
    private long seq;

    public InterestCalc(Map<String, Path> in, String parm, Clock clock, Consumer<String> sysout) {
        this.in = in;
        this.parm = Datasets.pad(parm, 10);
        this.clock = clock;
        this.sysout = sysout;
    }

    public List<String> transact() {
        return transact;
    }

    public List<String> accountFile() {
        return new ArrayList<>(accounts.values());
    }

    /** Runs the job. Outputs reflect work done so far if an {@link Abend} is thrown. */
    public void run() {
        sysout.accept("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tcat = open("TCATBALF", 50, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        for (String r : open("XREFFILE", 50, "ERROR OPENING CROSS REF FILE35")) {
            xref.putIfAbsent(r.substring(25, 36), r);
        }
        for (String r : open("DISCGRP", 50, "ERROR OPENING DALY REJECTS FILE")) {
            discgrp.put(r.substring(0, 16), r);
        }
        for (String r : open("ACCTFILE", 300, "ERROR OPENING ACCOUNT MASTER FILE")) {
            accounts.put(r.substring(0, 11), r);
        }

        String lastAcct = Datasets.pad("", 11);
        boolean first = true;
        String acct = null;
        String card = null;
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (String rec : tcat) {
            sysout.accept(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    update(acct, total);
                }
                first = false;
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accounts.get(acctId);
                if (acct == null) {
                    fail("ACCOUNT NOT FOUND: " + acctId, "ERROR READING ACCOUNT FILE", "0023");
                }
                String x = xref.get(acctId);
                if (x == null) {
                    fail("ACCOUNT NOT FOUND: " + acctId, "ERROR READING XREF FILE", "0023");
                }
                card = x.substring(0, 16);
            }
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            BigDecimal bal = Zoned.decode(rec.substring(17, 28), 2);
            String d = discgrp.get(acct.substring(112, 122) + type + cat);
            if (d == null) {
                sysout.accept("DISCLOSURE GROUP RECORD MISSING");
                sysout.accept("TRY WITH DEFAULT GROUP CODE");
                d = discgrp.get(Datasets.pad("DEFAULT", 10) + type + cat);
                if (d == null) {
                    fail(null, "ERROR READING DEFAULT DISCLOSURE GROUP", "0023");
                }
            }
            BigDecimal rate = Zoned.decode(d.substring(16, 22), 2);
            if (rate.signum() != 0) {
                BigDecimal monthly = Zoned.fit(Zoned.truncDivide(bal.multiply(rate), DIV), 9);
                total = Zoned.fit(total.add(monthly), 9);
                writeTx(acctId, acct, card, monthly);
            }
        }
        // D1 (preserved): the last account is never rewritten.
        sysout.accept("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private List<String> open(String dd, int lrecl, String errText) {
        Path p = in.get(dd);
        try {
            if (p == null || !Files.isReadable(p)) {
                throw new IOException("missing");
            }
            return Datasets.read(p, lrecl);
        } catch (IOException e) {
            fail(errText, null, "0035");
            return List.of();
        }
    }

    /** Emits the abend SYSOUT lines and throws. */
    private void fail(String line1, String line2, String status) {
        if (line1 != null) {
            sysout.accept(line1);
        }
        if (line2 != null) {
            sysout.accept(line2);
        }
        sysout.accept("FILE STATUS IS: NNNN" + status);
        sysout.accept("ABENDING PROGRAM");
        throw new Abend("U0999");
    }

    private void update(String acct, BigDecimal total) {
        BigDecimal bal = Zoned.decode(acct.substring(12, 24), 2);
        String newBal = Zoned.encode(Zoned.fit(bal.add(total), 10), 12);
        String zero = Zoned.encode(BigDecimal.ZERO.setScale(2), 12);
        String rec = acct.substring(0, 12) + newBal + acct.substring(24, 78) + zero + zero + acct.substring(102);
        accounts.put(acct.substring(0, 11), rec);
    }

    private void writeTx(String acctId, String acct, String card, BigDecimal monthly) {
        seq = (seq + 1) % 1_000_000;
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        StringBuilder sb = new StringBuilder();
        sb.append(parm).append(String.format("%06d", seq));
        sb.append("01").append("0005").append(Datasets.pad("System", 10));
        sb.append(Datasets.pad("Int. for a/c " + acct.substring(0, 11), 100));
        sb.append(Zoned.encode(monthly, 11));
        sb.append("000000000");
        sb.append(" ".repeat(110));
        sb.append(card).append(ts).append(ts);
        sb.append(" ".repeat(20));
        transact.add(sb.toString());
    }
}
