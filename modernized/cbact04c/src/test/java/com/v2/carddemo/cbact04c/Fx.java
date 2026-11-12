package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Inline fixture builder + runner shared by the tests. */
final class Fx {
    final List<String> tcat = new ArrayList<>();
    final List<String> xref = new ArrayList<>();
    final List<String> acct = new ArrayList<>();
    final List<String> disc = new ArrayList<>();

    static String pad(String s, int n) { return FixedRecord.pad(s, n); }

    Fx tcat(long acct, String type, String cat, long cents) {
        tcat.add(pad(String.format("%011d", acct) + type + cat + ZonedDecimal.encode(cents, 11), 50));
        return this;
    }
    Fx xref(String card, long acct) {
        xref.add(pad(card, 25) + String.format("%011d", acct));
        return this;
    }
    Fx acct(long id, long bal, long credit, long debit, String group) {
        String r = pad(String.format("%011d", id) + "Y" + ZonedDecimal.encode(bal, 12), 300);
        r = FixedRecord.put(r, 79, ZonedDecimal.encode(credit, 12));
        r = FixedRecord.put(r, 91, ZonedDecimal.encode(debit, 12));
        r = FixedRecord.put(r, 113, pad(group, 10));
        acct.add(r);
        return this;
    }
    Fx disc(String group, String type, String cat, long rate) {
        disc.add(pad(pad(group, 10) + type + cat + ZonedDecimal.encode(rate, 6), 50));
        return this;
    }

    static void write(Path f, List<String> recs) throws IOException {
        Files.writeString(f, String.join("\n", recs) + (recs.isEmpty() ? "" : "\n"), StandardCharsets.ISO_8859_1);
    }

    static final class Result {
        int rc; List<String> tran; List<String> acct; List<String> sysout; String err;
    }

    Result run(Path tmp) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = tmp.resolve("out");
        write(in.resolve("TCATBALF.txt"), tcat);
        write(in.resolve("XREFFILE.txt"), xref);
        write(in.resolve("ACCTFILE.txt"), acct);
        write(in.resolve("DISCGRP.txt"), disc);
        return run(in, out);
    }

    static Result run(Path in, Path out) throws IOException {
        java.io.ByteArrayOutputStream eb = new java.io.ByteArrayOutputStream();
        Result r = new Result();
        r.rc = Cbact04c.run("2022071800", Cbact04c.fixedClock("2022071800000000"), in, out, new PrintStream(eb));
        r.err = eb.toString();
        r.tran = Files.readAllLines(out.resolve("TRANSACT.txt"), StandardCharsets.ISO_8859_1);
        r.acct = Files.readAllLines(out.resolve("ACCTFILE.txt"), StandardCharsets.ISO_8859_1);
        r.sysout = Files.readAllLines(out.resolve("SYSOUT.txt"), StandardCharsets.ISO_8859_1);
        return r;
    }

    static String repoRoot() { return System.getProperty("repo.root", "../.."); }
}
