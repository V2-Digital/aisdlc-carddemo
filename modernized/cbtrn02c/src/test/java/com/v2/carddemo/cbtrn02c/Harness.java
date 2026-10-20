package com.v2.carddemo.cbtrn02c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Runs Cbtrn02c on an input directory (DD.txt files) the way the harness does. */
final class Harness {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path SAMPLE = ROOT.resolve("app/data/ASCII");
    static final String FROZEN = "2022071800000000";

    final int rc;
    final String stdout;
    final String stderr;
    final Path dir;

    private Harness(int rc, String stdout, String stderr, Path dir) {
        this.rc = rc;
        this.stdout = stdout;
        this.stderr = stderr;
        this.dir = dir;
    }

    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.ISO_8859_1);
    }

    /** inputs: DD name -> text. Missing DDs are left unset. */
    static Harness run(Path work, Map<String, String> inputs, String clock) throws IOException {
        Map<String, String> env = new HashMap<>();
        if (clock != null) {
            env.put("CARDDEMO_CURRENT_DATE", clock);
        }
        for (var e : inputs.entrySet()) {
            Path p = work.resolve(e.getKey() + ".txt");
            Files.writeString(p, e.getValue(), StandardCharsets.ISO_8859_1);
            env.put("DD_" + e.getKey(), p.toString());
        }
        for (String dd : new String[] {"TRANFILE", "DALYREJS"}) {
            env.put("DD_" + dd, work.resolve(dd + ".txt").toString());
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int rc = new Cbtrn02c(env, Cbtrn02c.clockFrom(env), new PrintStream(o, true), new PrintStream(e, true)).run();
        return new Harness(rc, o.toString(StandardCharsets.ISO_8859_1).replace("\r", ""), e.toString(), work);
    }

    /** Inputs from a directory of DD.txt files (CRs stripped like run.sh). */
    static Map<String, String> inputsFrom(Path dir, Path... fallback) throws IOException {
        Map<String, String> m = new HashMap<>();
        for (String dd : new String[] {"DALYTRAN", "XREFFILE", "ACCTFILE", "TCATBALF"}) {
            m.put(dd, read(dir.resolve(dd + ".txt")).replace("\r", ""));
        }
        return m;
    }

    static Map<String, String> stock() throws IOException {
        Map<String, String> m = new HashMap<>();
        m.put("DALYTRAN", read(SAMPLE.resolve("dailytran.txt")).replace("\r", ""));
        m.put("XREFFILE", read(SAMPLE.resolve("cardxref.txt")).replace("\r", ""));
        m.put("ACCTFILE", read(SAMPLE.resolve("acctdata.txt")).replace("\r", ""));
        m.put("TCATBALF", read(SAMPLE.resolve("tcatbal.txt")).replace("\r", ""));
        return m;
    }

    String out(String dd) throws IOException {
        return read(dir.resolve(dd + ".txt"));
    }

    // ---- synthetic record builders (same layout as legacy/tests fixtures) ----
    static String z(long v, int n) {
        return Zoned.encode(v, n);
    }

    static String tran(int id, String type, int cat, long amt, String card, String origTs) {
        return String.format("%016d%s%04d", id, type, cat) + fit("POS TERM", 10) + fit("desc", 100) + z(amt, 11)
            + "800000000" + fit("Merchant", 50) + fit("City", 50) + fit("12345", 10) + card + fit(origTs, 26)
            + fit("", 26) + fit("", 20) + "\n";
    }

    static String acct(int id, long cur, long limit, String expiry, long cc, long cd) {
        return Datasets.fit(String.format("%011dY", id) + z(cur, 12) + z(limit, 12) + z(0, 12) + "2014-11-20" + expiry
            + "2025-05-20" + z(cc, 12) + z(cd, 12) + "12345", 300) + "\n";
    }

    static String xref(String card, int acct) {
        return Datasets.fit(card + "000000001" + String.format("%011d", acct), 50) + "\n";
    }

    static String tcat(int acct, String type, int cat, long bal) {
        return Datasets.fit(String.format("%011d%s%04d", acct, type, cat) + z(bal, 11), 28) + "0".repeat(22) + "\n";
    }

    static String fit(String s, int n) {
        return Datasets.fit(s, n);
    }

    static Map<String, String> of(String tran, String xref, String acct, String tcat) {
        Map<String, String> m = new HashMap<>();
        m.put("DALYTRAN", tran);
        m.put("XREFFILE", xref);
        m.put("ACCTFILE", acct);
        m.put("TCATBALF", tcat);
        return m;
    }
}
