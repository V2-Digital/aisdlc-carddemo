package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Test fixture: builds crafted inputs, runs Main in a temp dir, collects outputs. */
final class Fx {
    static final String CLOCK = "2022071800000000";
    final Path dir;
    String parm = "2022071800";
    List<String> tcat = new ArrayList<>(), xref = new ArrayList<>(), disc = new ArrayList<>(), acct = new ArrayList<>();
    int rc;
    String sysout, stderr;

    Fx(Path dir) { this.dir = dir; }

    static Path root() {
        String r = System.getProperty("repo.root");
        if (r == null) throw new IllegalStateException("repo.root system property missing");
        return Path.of(r).normalize();
    }

    static String pad(String s, int n) { return s + " ".repeat(n - s.length()); }
    static String z(String v, int intDigits) { return Zoned.encode(new BigDecimal(v), intDigits); }

    static String tcatRec(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + z(bal, 9), 50);
    }
    static String xrefRec(String card, String acct) { return pad(card + "000000001" + acct, 50); }
    static String discRec(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + z(rate, 4), 50);
    }
    static String acctRec(String id, String bal, String group) {
        String s = id + "Y" + z(bal, 10) + z("5000.00", 10) + z("5000.00", 10) + "2020-01-01" + "2030-01-01" + "2025-01-01"
                + z("10.00", 10) + z("20.00", 10) + pad("", 10) + pad(group, 10);
        return pad(s, 300);
    }

    /** Standard single DEFAULT 01/0001 rate. */
    Fx rate(String r) { disc.add(discRec("DEFAULT", "01", "0001", r)); return this; }

    Fx run() throws IOException {
        write("TCATBALF", tcat); write("XREFFILE", xref); write("DISCGRP", disc); write("ACCTFILE", acct);
        String dump = System.getenv("FX_DUMP"); // optional: keep inputs for COBOL cross-check
        if (dump != null) {
            Path d = Path.of(dump, String.valueOf(System.nanoTime()));
            Files.createDirectories(d);
            for (String dd : new String[] {"TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE"})
                Files.copy(dir.resolve(dd + ".txt"), d.resolve(dd + ".txt"));
        }
        return exec();
    }

    Fx exec() throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
        rc = Main.run(new String[] {"--PARM=" + parm, "--CURRENT-DATE=" + CLOCK,
                "--TCATBALF=" + dir.resolve("TCATBALF.txt"), "--XREFFILE=" + dir.resolve("XREFFILE.txt"),
                "--DISCGRP=" + dir.resolve("DISCGRP.txt"), "--ACCTFILE=" + dir.resolve("ACCTFILE.txt"),
                "--ACCTFILE-OUT=" + dir.resolve("ACCTFILE.out"), "--TRANSACT=" + dir.resolve("TRANSACT.out")},
                new PrintStream(o, true, StandardCharsets.ISO_8859_1), new PrintStream(e, true, StandardCharsets.ISO_8859_1));
        sysout = o.toString(StandardCharsets.ISO_8859_1);
        stderr = e.toString(StandardCharsets.ISO_8859_1);
        return this;
    }

    void write(String dd, List<String> recs) throws IOException {
        Files.writeString(dir.resolve(dd + ".txt"), String.join("\n", recs) + (recs.isEmpty() ? "" : "\n"),
                StandardCharsets.ISO_8859_1);
    }

    List<String> lines(String name) throws IOException {
        Path p = dir.resolve(name);
        if (!Files.exists(p)) return null;
        String s = Files.readString(p, StandardCharsets.ISO_8859_1);
        return s.isEmpty() ? List.of() : List.of(s.split("\n", -1)).subList(0, s.split("\n", -1).length - 1);
    }
    List<String> trans() throws IOException { return lines("TRANSACT.out"); }
    List<String> accts() throws IOException { return lines("ACCTFILE.out"); }
    static String amt(String tranRec) { return tranRec.substring(132, 143); }
}
