package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Test fixture builder + runner. */
final class Fx {
    static final Path ROOT = findRoot();
    final List<String> tcat = new ArrayList<>();
    final List<String> xref = new ArrayList<>();
    final List<String> disc = new ArrayList<>();
    final List<String> acct = new ArrayList<>();
    String sysout;
    int rc;
    Path out;

    static Path findRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(p.resolve("legacy/golden"))) {
            p = p.getParent();
        }
        return p;
    }

    static String p(String s, int n) {
        return FixedRecords.pad(s, n);
    }

    Fx tcat(String acct, String type, String cat, String bal) {
        tcat.add(p(acct + type + cat + bal, 50));
        return this;
    }

    Fx xref(String card, String acct) {
        xref.add(p(card, 25) + acct);
        return this;
    }

    Fx disc(String group, String type, String cat, String rate) {
        disc.add(p(p(group, 10) + type + cat + rate, 50));
        return this;
    }

    /** bal is 12-char zoned; credit/debit are set to a non-zero value to prove clearing. */
    Fx acct(String id, String bal, String group) {
        String c = "00000000999{";
        String r = p(id + "Y" + bal, 78) + c + c;
        acct.add(p(p(r, 112) + p(group, 10) + "TAIL", 300));
        return this;
    }

    Fx run() throws IOException {
        Path in = Files.createTempDirectory("in");
        out = Files.createTempDirectory("out");
        FixedRecords.write(in.resolve("TCATBALF.txt"), tcat);
        FixedRecords.write(in.resolve("XREFFILE.txt"), xref);
        FixedRecords.write(in.resolve("DISCGRP.txt"), disc);
        FixedRecords.write(in.resolve("ACCTFILE.txt"), acct);
        return run(in, out);
    }

    Fx run(Path in, Path out) throws IOException {
        this.out = out;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(b, true, StandardCharsets.ISO_8859_1);
        Clock c = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), java.time.ZoneOffset.UTC);
        rc = new InterestCalc(ps, new PrintStream(new ByteArrayOutputStream()), c).run("2022071800", in, out);
        sysout = b.toString(StandardCharsets.ISO_8859_1);
        return this;
    }

    List<String> trans() throws IOException {
        return FixedRecords.read(out.resolve("TRANSACT.txt"), 350);
    }

    List<String> accts() throws IOException {
        return FixedRecords.read(out.resolve("ACCTFILE.txt"), 300);
    }

    /** Standard single-rate fixture: DEFAULT 01/0001 at the given rate. */
    static Fx basic(String rate) {
        return new Fx().disc("DEFAULT", "01", "0001", rate);
    }
}
