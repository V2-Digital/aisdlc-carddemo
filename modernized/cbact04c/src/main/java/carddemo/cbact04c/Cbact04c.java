package carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Java 21 port of CBACT04C (interest calculation). Behaviour-preserving, defects included. */
public final class Cbact04c {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int DISC_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int TRAN_LEN = 350;

    private static final class Abend extends Exception {}

    private final Map<String, String> env;
    private final PrintStream out;
    private List<String> accounts;
    private Path acctPath;

    private Cbact04c(Map<String, String> env, PrintStream out) {
        this.env = env;
        this.out = out;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: Cbact04c <PARM yyyymmddnn>");
            System.exit(2);
        }
        PrintStream out = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), false,
                java.nio.charset.StandardCharsets.ISO_8859_1);
        int rc = run(System.getenv(), args[0], out);
        out.flush();
        System.exit(rc);
    }

    /** Runs the job; returns the exit code (0, or 16 on abend). */
    public static int run(Map<String, String> env, String parm, PrintStream out) {
        return new Cbact04c(env, out).execute(Datasets.pad(parm, 10));
    }

    private void display(String s) {
        out.print(s + "\n");
    }

    private Path dd(String name) {
        String v = env.get("DD_" + name);
        return v == null ? Path.of(name) : Path.of(v);
    }

    private List<String> open(String name, int lrecl, String msg, boolean suffixStatus) throws Abend {
        try {
            return Datasets.read(dd(name), lrecl);
        } catch (IOException e) {
            display(msg + (suffixStatus ? "35" : ""));
            display("FILE STATUS IS: NNNN0035");
            throw abend();
        }
    }

    private Abend abend() {
        display("ABENDING PROGRAM");
        return new Abend();
    }

    private String clock() {
        String c = env.get("CARDDEMO_CURRENT_DATE");
        if (c != null && !c.isEmpty()) {
            return c;
        }
        ZonedDateTime n = ZonedDateTime.now(ZoneOffset.UTC);
        return n.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%02d", n.getNano() / 10_000_000);
    }

    private int execute(String parm) {
        display("START OF EXECUTION OF PROGRAM CBACT04C");
        List<String> tran = new ArrayList<>();
        int rc = 0;
        try {
            List<String> tcatbal = open("TCATBALF", TCATBAL_LEN, "ERROR OPENING TRANSACTION CATEGORY BALANCE", false);
            List<String> xref = open("XREFFILE", XREF_LEN, "ERROR OPENING CROSS REF FILE", true);
            List<String> disc = open("DISCGRP", DISC_LEN, "ERROR OPENING DALY REJECTS FILE", false);
            accounts = open("ACCTFILE", ACCT_LEN, "ERROR OPENING ACCOUNT MASTER FILE", false);
            acctPath = dd("ACCTFILE");
            try {
                Files.write(dd("TRANSACT"), new byte[0]);
            } catch (IOException e) {
                display("ERROR OPENING TRANSACTION FILE");
                display("FILE STATUS IS: NNNN0035");
                throw abend();
            }
            process(tcatbal, xref, disc, tran, parm);
            display("END OF EXECUTION OF PROGRAM CBACT04C");
        } catch (Abend a) {
            System.err.println("CEE3ABD: ABEND U0999");
            rc = 16;
        }
        try {
            if (accounts != null) {
                Datasets.write(acctPath, accounts);
            }
            if (accounts != null) {
                Datasets.write(dd("TRANSACT"), tran);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return rc;
    }

    private void process(List<String> tcatbal, List<String> xref, List<String> disc, List<String> tran,
            String parm) throws Abend {
        Map<String, String> discByKey = new HashMap<>();
        for (String d : disc) {
            discByKey.putIfAbsent(d.substring(0, 16), d);
        }
        String lastAcct = "           ";
        boolean first = true;
        int suffix = 0;
        int cur = -1;
        String acct = null;
        String card = null;
        BigDecimal total = BigDecimal.ZERO.setScale(2);

        for (String rec : tcatbal) {
            display(rec);
            String acctId = rec.substring(0, 11);
            String type = rec.substring(11, 13);
            String cat = rec.substring(13, 17);
            BigDecimal bal = Zoned.parse(rec.substring(17, 28), 2);
            if (!acctId.equals(lastAcct)) {
                if (!first) {
                    updateAccount(cur, total);
                } else {
                    first = false;
                }
                total = BigDecimal.ZERO.setScale(2);
                lastAcct = acctId;
                cur = -1;
                for (int i = 0; i < accounts.size(); i++) {
                    if (accounts.get(i).startsWith(acctId)) {
                        cur = i;
                        break;
                    }
                }
                if (cur < 0) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING ACCOUNT FILE");
                    display("FILE STATUS IS: NNNN0023");
                    throw abend();
                }
                acct = accounts.get(cur);
                card = null;
                for (String x : xref) {
                    if (x.substring(25, 36).equals(acctId)) {
                        card = x.substring(0, 16);
                        break;
                    }
                }
                if (card == null) {
                    display("ACCOUNT NOT FOUND: " + acctId);
                    display("ERROR READING XREF FILE");
                    display("FILE STATUS IS: NNNN0023");
                    throw abend();
                }
            }
            String group = acct.substring(112, 122);
            String d = discByKey.get(group + type + cat);
            if (d == null) {
                display("DISCLOSURE GROUP RECORD MISSING");
                display("TRY WITH DEFAULT GROUP CODE");
                d = discByKey.get("DEFAULT   " + type + cat);
                if (d == null) {
                    display("ERROR READING DEFAULT DISCLOSURE GROUP");
                    display("FILE STATUS IS: NNNN0023");
                    throw abend();
                }
            }
            BigDecimal rate = Zoned.parse(d.substring(16, 22), 2);
            if (rate.signum() != 0) {
                BigDecimal monthly = Zoned.truncate(
                        bal.multiply(rate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 2, 9);
                total = Zoned.truncate(total.add(monthly), 2, 9);
                suffix = (suffix + 1) % 1_000_000;
                tran.add(tranRecord(parm, suffix, acctId, monthly, card));
                // 1400-COMPUTE-FEES: empty in the legacy program (D2)
            }
        }
        // End of file: the legacy loop exits before the final account is rewritten (D1).
    }

    private void updateAccount(int idx, BigDecimal total) {
        String a = accounts.get(idx);
        BigDecimal bal = Zoned.parse(a.substring(12, 24), 2);
        bal = Zoned.truncate(bal.add(total), 2, 10);
        String zero = Zoned.format(BigDecimal.ZERO, 2, 12);
        accounts.set(idx, a.substring(0, 12) + Zoned.format(bal, 2, 12) + a.substring(24, 78)
                + zero + zero + a.substring(102));
    }

    private String tranRecord(String parm, int suffix, String acctId, BigDecimal monthly, String card) {
        String c = clock();
        String ts = c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
                + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14) + "."
                + c.substring(14, 16) + "0000";
        StringBuilder sb = new StringBuilder();
        sb.append(parm).append(String.format("%06d", suffix));
        sb.append("01").append("0005");
        sb.append(Datasets.pad("System", 10));
        sb.append(Datasets.pad("Int. for a/c " + acctId, 100));
        sb.append(Zoned.format(monthly, 2, 11));
        sb.append("000000000");
        sb.append(" ".repeat(110));
        sb.append(card);
        sb.append(ts).append(ts);
        return Datasets.pad(sb.toString(), TRAN_LEN);
    }
}
