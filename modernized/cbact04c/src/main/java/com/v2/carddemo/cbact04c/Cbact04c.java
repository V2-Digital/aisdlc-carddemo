package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
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

/** Like-for-like Java port of CBACT04C (interest calculation). Legacy defects are preserved on purpose. */
public final class Cbact04c {
    /** Thrown where the COBOL program would PERFORM 9999-ABEND-PROGRAM. */
    private static final class Abend extends RuntimeException {
        Abend() {
            super(null, null, false, false);
        }
    }

    private static final int TCATBAL_LEN = 50;
    private static final int XREF_LEN = 50;
    private static final int ACCT_LEN = 300;
    private static final int DISC_LEN = 50;
    private static final int TRAN_LEN = 350;

    private final Path tcatbalf;
    private final Path xreffile;
    private final Path acctfile;
    private final Path discgrp;
    private final Path transact;
    private final Clock clock;
    private final PrintStream out;
    private final PrintStream err;

    public Cbact04c(Path tcatbalf, Path xreffile, Path acctfile, Path discgrp, Path transact,
                    Clock clock, PrintStream out, PrintStream err) {
        this.tcatbalf = tcatbalf;
        this.xreffile = xreffile;
        this.acctfile = acctfile;
        this.discgrp = discgrp;
        this.transact = transact;
        this.clock = clock;
        this.out = out;
        this.err = err;
    }

    /** Runs the job and returns the return code (0, or 16 on abend). */
    public int run(String parm) {
        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            return execute(parm);
        } catch (Abend a) {
            out.println("ABENDING PROGRAM");
            err.println("CEE3ABD: ABEND U0999");
            return 16;
        } finally {
            out.flush();
            err.flush();
        }
    }

    private int execute(String parm) {
        if (parm == null || parm.length() != 10) {
            out.println("INVALID PARM: EXPECTED 10 CHARACTERS");
            throw new Abend();
        }
        List<String> bal = open(tcatbalf, TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE");
        List<String> xrefLines = open(xreffile, XREF_LEN, "ERROR OPENING CROSS REF FILE");
        List<String> discLines = open(discgrp, DISC_LEN, "ERROR OPENING DALY REJECTS FILE");
        List<String> acctLines = open(acctfile, ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE");

        Map<String, String> xref = new HashMap<>();
        for (String r : xrefLines) {
            xref.putIfAbsent(r.substring(25, 36), r.substring(0, 16)); // D7: first card only
        }
        Map<String, BigDecimal> rates = new HashMap<>();
        for (String r : discLines) {
            rates.put(r.substring(0, 16), decodeField(r.substring(16, 22)));
        }
        Map<String, String> accts = new LinkedHashMap<>();
        for (String r : acctLines) {
            accts.put(r.substring(0, 11), r);
        }
        List<String> tran = new ArrayList<>();

        String lastAcct = "";
        boolean first = true;
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        int suffix = 0;
        String card = null;
        String groupId = null;

        for (String line : bal) {
            out.println(line);
            String acctId = line.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(accts, lastAcct, total);
                } else {
                    first = false;
                }
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                if (!accts.containsKey(acctId)) {
                    notFound(acctId, "ERROR READING ACCOUNT FILE");
                }
                groupId = accts.get(acctId).substring(112, 122);
                card = xref.get(acctId);
                if (card == null) {
                    notFound(acctId, "ERROR READING XREF FILE");
                }
            }
            String typeCat = line.substring(11, 17);
            BigDecimal rate = rates.get(groupId + typeCat);
            if (rate == null) {
                out.println("DISCLOSURE GROUP RECORD MISSING");
                out.println("TRY WITH DEFAULT GROUP CODE");
                rate = rates.get("DEFAULT   " + typeCat);
                if (rate == null) {
                    out.println("ERROR READING DEFAULT DISCLOSURE GROUP");
                    out.println("FILE STATUS IS: NNNN0023");
                    throw new Abend();
                }
            }
            if (rate.signum() != 0) {
                BigDecimal balance = decodeField(line.substring(17, 28));
                BigDecimal monthly = Zoned.wrap(
                        balance.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 11); // D2, D6
                total = Zoned.wrap(total.add(monthly), 11);
                suffix = (suffix + 1) % 1_000_000;
                tran.add(tranRecord(parm, suffix, acctId, monthly, card));
            }
        }
        // D1: the last account is never rewritten.

        write(acctfile, accts.values());
        write(transact, tran);
        out.println("END OF EXECUTION OF PROGRAM CBACT04C");
        return 0;
    }

    private void notFound(String acctId, String msg) {
        out.println("ACCOUNT NOT FOUND: " + acctId);
        out.println(msg);
        out.println("FILE STATUS IS: NNNN0023");
        throw new Abend();
    }

    private static void updateAccount(Map<String, String> accts, String id, BigDecimal total) {
        String r = accts.get(id);
        BigDecimal curr = decodeField(r.substring(12, 24));
        String zero = "00000000000{";
        accts.put(id, r.substring(0, 12) + Zoned.encode(curr.add(total), 12)
                + r.substring(24, 78) + zero + zero + r.substring(102));
    }

    private String tranRecord(String parm, int suffix, String acctId, BigDecimal amt, String card) {
        StringBuilder sb = new StringBuilder(" ".repeat(TRAN_LEN));
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        put(sb, 0, parm + String.format("%06d", suffix));
        put(sb, 16, "01");
        put(sb, 18, "0005");
        put(sb, 22, "System");
        put(sb, 32, "Int. for a/c " + acctId);
        put(sb, 132, Zoned.encode(amt, 11));
        put(sb, 143, "000000000");
        put(sb, 262, card);
        put(sb, 278, ts);
        put(sb, 304, ts);
        return sb.toString();
    }

    private static void put(StringBuilder sb, int off, String s) {
        sb.replace(off, off + s.length(), s);
    }

    private static BigDecimal decodeField(String s) {
        try {
            return Zoned.decode(s);
        } catch (IllegalArgumentException e) {
            throw new Abend(); // malformed numeric: treated as an I/O error
        }
    }

    /** Reads a fixed-width text file: CR stripped, lines padded to the record length. */
    private List<String> open(Path p, int len, String msg) {
        try {
            List<String> rows = new ArrayList<>();
            String text = Files.readString(p, StandardCharsets.ISO_8859_1).replace("\r", "");
            if (!text.isEmpty()) {
                for (String l : text.split("\n", -1)) {
                    rows.add(l);
                }
                if (text.endsWith("\n")) {
                    rows.remove(rows.size() - 1);
                }
            }
            rows.replaceAll(l -> l.length() >= len ? l.substring(0, len) : l + " ".repeat(len - l.length()));
            return rows;
        } catch (IOException e) {
            out.println(msg);
            out.println("FILE STATUS IS: NNNN0035");
            throw new Abend();
        }
    }

    private void write(Path p, Iterable<String> rows) {
        StringBuilder sb = new StringBuilder();
        for (String r : rows) {
            sb.append(r).append('\n');
        }
        try {
            Files.writeString(p, sb, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            out.println("ERROR WRITING TRANSACTION RECORD");
            out.println("FILE STATUS IS: NNNN0035");
            throw new Abend();
        }
    }
}
