package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Java port of CBACT04C (interest calculation). Behaviour-preserving, see spec.md. */
public final class InterestCalc {

    /** Outcome of a run: exit code, SYSOUT lines and the output datasets. */
    public record Result(int rc, List<String> sysout, List<String> transact, List<String> accounts) {}

    /** COBOL abend (CEE3ABD); the SYSOUT lines have already been written. */
    private static final class Abend extends RuntimeException {
        Abend() { super(null, null, false, false); }
    }

    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private final List<String> out = new ArrayList<>();
    private final List<String> tran = new ArrayList<>();

    private InterestCalc() {}

    public static Result run(String parm, Path inDir, Supplier<LocalDateTime> clock) {
        InterestCalc c = new InterestCalc();
        try {
            return c.execute(parm, inDir, clock);
        } catch (Abend a) {
            c.out.add("ABENDING PROGRAM");
            return new Result(16, c.out, List.of(), List.of());
        }
    }

    // ---- program ----------------------------------------------------------

    private Result execute(String parm, Path inDir, Supplier<LocalDateTime> clock) {
        String parmDate = pad(parm.length() > 10 ? parm.substring(0, 10) : parm, 10);
        out.add("START OF EXECUTION OF PROGRAM CBACT04C");

        TreeMap<String, String> tcat = load(inDir, "TCATBALF", 50, 17,
                "ERROR OPENING TRANSACTION CATEGORY BALANCE", "ERROR READING TRANSACTION CATEGORY FILE");
        load(inDir, "XREFFILE", 50, -1,
                "ERROR OPENING CROSS REF FILE35", "ERROR READING XREF FILE");
        // XREFFILE is read by account (alternate key), first card in file order wins.
        List<String> xrefRaw = rawLines(inDir, "XREFFILE", 50);
        Map<String, String> xrefByAcct = new HashMap<>();
        for (String r : xrefRaw) {
            xrefByAcct.putIfAbsent(r.substring(25, 36), r);
        }
        Map<String, String> disc = load(inDir, "DISCGRP", 50, 16,
                "ERROR OPENING DALY REJECTS FILE", "ERROR READING DISCLOSURE GROUP FILE");
        TreeMap<String, String> accts = load(inDir, "ACCTFILE", 300, 11,
                "ERROR OPENING ACCOUNT MASTER FILE", "ERROR READING ACCOUNT FILE");

        String lastAcct = pad("", 11);
        boolean first = true;
        BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
        int suffix = 0;
        String acct = null;
        String card = null;
        BigDecimal rate = BigDecimal.ZERO;

        for (String rec : tcat.values()) {
            out.add(rec);
            String acctId = rec.substring(0, 11);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(accts, acct, totalInt);
                } else {
                    first = false;
                }
                totalInt = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                acct = accts.get(acctId);
                if (acct == null) {
                    notFound(acctId, "ERROR READING ACCOUNT FILE");
                }
                String x = xrefByAcct.get(acctId);
                if (x == null) {
                    notFound(acctId, "ERROR READING XREF FILE");
                }
                card = x.substring(0, 16);
            }
            String group = acct.substring(112, 122);
            String typeCat = rec.substring(11, 17);
            String d = disc.get(group + typeCat);
            if (d == null) {
                out.add("DISCLOSURE GROUP RECORD MISSING");
                out.add("TRY WITH DEFAULT GROUP CODE");
                d = disc.get(pad("DEFAULT", 10) + typeCat);
                if (d == null) {
                    status("ERROR READING DEFAULT DISCLOSURE GROUP", "0023");
                }
            }
            rate = Zoned.decode(d.substring(16, 22));
            if (rate.signum() != 0) {
                BigDecimal bal = Zoned.decode(rec.substring(17, 28));
                BigDecimal monthly = bal.multiply(rate).divide(TWELVE_HUNDRED, 2, RoundingMode.DOWN);
                monthly = Zoned.wrap(monthly, 11);
                totalInt = Zoned.wrap(totalInt.add(monthly), 11);
                suffix = (suffix + 1) % 1_000_000;
                writeTran(parmDate, suffix, acct, monthly, card, clock.get());
            }
        }
        // F1: the last account is never updated (COBOL paragraph 1050 is unreachable at EOF).

        out.add("END OF EXECUTION OF PROGRAM CBACT04C");
        return new Result(0, out, tran, new ArrayList<>(accts.values()));
    }

    private void updateAccount(TreeMap<String, String> accts, String acct, BigDecimal totalInt) {
        BigDecimal bal;
        try {
            bal = Zoned.decode(acct.substring(12, 24));
        } catch (NumberFormatException e) {
            status("ERROR READING ACCOUNT FILE", "0090");
            return;
        }
        String zero = Zoned.encode(BigDecimal.ZERO, 12);
        String upd = acct.substring(0, 12) + Zoned.encode(Zoned.wrap(bal.add(totalInt), 12), 12)
                + acct.substring(24, 78) + zero + zero + acct.substring(102);
        accts.put(acct.substring(0, 11), upd);
    }

    private void writeTran(String parmDate, int suffix, String acct, BigDecimal amt, String card,
            LocalDateTime now) {
        String ts = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", now.getYear(),
                now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(),
                now.getSecond(), now.getNano() / 10_000_000);
        String r = parmDate + String.format("%06d", suffix)
                + "01" + "0005"
                + pad("System", 10)
                + pad("Int. for a/c " + acct.substring(0, 11), 100)
                + Zoned.encode(amt, 11)
                + "000000000"
                + pad("", 110)
                + card
                + ts + ts
                + pad("", 20);
        tran.add(r);
    }

    // ---- file loading / errors ---------------------------------------------

    private List<String> rawLines(Path dir, String dd, int lrecl) {
        try {
            return FixedFile.read(dir.resolve(dd + ".txt"), lrecl);
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Loads a dataset keyed on its first {@code keyLen} columns (keyLen -1: no key, file order). */
    private TreeMap<String, String> load(Path dir, String dd, int lrecl, int keyLen,
            String openMsg, String readMsg) {
        Path p = dir.resolve(dd + ".txt");
        if (!Files.isRegularFile(p)) {
            status(openMsg, "0035");
        }
        List<String> lines;
        try {
            lines = FixedFile.read(p, lrecl);
        } catch (IOException e) {
            status(readMsg, "0090");
            return null;
        }
        TreeMap<String, String> m = new TreeMap<>();
        int n = 0;
        for (String l : lines) {
            String key = keyLen < 0 ? String.format("%08d", n++) : l.substring(0, keyLen);
            if (m.put(key, l) != null) {
                status(readMsg, "0090");
            }
            if (dd.equals("TCATBALF")) {
                checkZoned(l.substring(17, 28), readMsg);
            } else if (dd.equals("DISCGRP")) {
                checkZoned(l.substring(16, 22), readMsg);
            }
        }
        return m;
    }

    private void checkZoned(String f, String readMsg) {
        try {
            Zoned.decode(f);
        } catch (NumberFormatException e) {
            status(readMsg, "0090");
        }
    }

    private void notFound(String acctId, String msg) {
        out.add("ACCOUNT NOT FOUND: " + acctId);
        status(msg, "0023");
    }

    private void status(String msg, String code) {
        out.add(msg);
        out.add("FILE STATUS IS: NNNN" + code);
        throw new Abend();
    }

    private static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }
}
