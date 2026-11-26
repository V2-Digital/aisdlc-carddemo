package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

final class Support {
    static final Path ROOT = Path.of(System.getProperty("repo.root", "../..")).toAbsolutePath().normalize();
    static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    record Result(int rc, String sysout, Path out) {
        String file(String name) throws IOException {
            return Files.readString(out.resolve(name + ".txt"), StandardCharsets.ISO_8859_1);
        }
    }

    static Path ascii(String f) {
        return ROOT.resolve("app/data/ASCII").resolve(f);
    }

    static Path golden(String scenario, String f) {
        return ROOT.resolve("legacy/golden").resolve(scenario).resolve(f);
    }

    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.ISO_8859_1);
    }

    /** Stock sample inputs into a new temp dir. */
    static Path stockInputs() throws IOException {
        Path in = Files.createTempDirectory("in");
        Files.copy(ascii("tcatbal.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(ascii("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ascii("acctdata.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(ascii("discgrp.txt"), in.resolve("DISCGRP.txt"));
        return in;
    }

    static void put(Path dir, String dd, String content) throws IOException {
        Files.writeString(dir.resolve(dd + ".txt"), content, StandardCharsets.ISO_8859_1);
    }

    static Result run(String parm, Path in) throws IOException {
        Path out = Files.createTempDirectory("out");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos, true, StandardCharsets.ISO_8859_1);
        int rc = new InterestCalc(CLOCK, ps).run(parm, in, out);
        return new Result(rc, bos.toString(StandardCharsets.ISO_8859_1), out);
    }

    static String sp(String s, int n) {
        return RecordFile.pad(s, n);
    }

    /** TCATBAL record: acct, type, cat, balance (zoned text). */
    static String tcat(String acct, String type, String cat, String bal) {
        return sp(acct + type + cat + bal, 50) + "\n";
    }

    static String disc(String group, String type, String cat, String rate) {
        return sp(sp(group, 10) + type + cat + rate, 50) + "\n";
    }

    static String xref(String card, String acct) {
        return sp(card + "         " + acct, 50) + "\n";
    }

    /** Account record 300 bytes: id, balance, credit, debit, group. */
    static String acct(String id, String bal, String cred, String deb, String group) {
        StringBuilder b = new StringBuilder(" ".repeat(300));
        b.replace(0, 11, id);
        b.replace(11, 12, "Y");
        b.replace(12, 24, bal);
        b.replace(78, 90, cred);
        b.replace(90, 102, deb);
        b.replace(112, 122, sp(group, 10));
        return b + "\n";
    }
}
