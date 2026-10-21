package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test fixtures: stock inputs, record builders, run helper. */
final class Fx {
    static final Path ROOT = Path.of("../..");
    static final Path SAMPLE = ROOT.resolve("app/data/ASCII");
    static final Path GOLDEN = ROOT.resolve("legacy/golden");
    static final String CLOCK = "2022071800000000";

    record Result(int rc, String sysout, String syserr, Path out) {}

    static Path stockInputs(Path dir) throws IOException {
        copy(dir, "TCATBALF", "tcatbal.txt");
        copy(dir, "XREFFILE", "cardxref.txt");
        copy(dir, "ACCTFILE", "acctdata.txt");
        copy(dir, "DISCGRP", "discgrp.txt");
        return dir;
    }

    private static void copy(Path dir, String dd, String sample) throws IOException {
        String s = Files.readString(SAMPLE.resolve(sample), StandardCharsets.ISO_8859_1).replace("\r", "");
        Files.writeString(dir.resolve(dd + ".txt"), s, StandardCharsets.ISO_8859_1);
    }

    static List<String> lines(Path f) throws IOException {
        return Files.readAllLines(f, StandardCharsets.ISO_8859_1);
    }

    static void put(Path dir, String dd, List<String> lines) throws IOException {
        Files.writeString(dir.resolve(dd + ".txt"), String.join("\n", lines) + (lines.isEmpty() ? "" : "\n"),
            StandardCharsets.ISO_8859_1);
    }

    static Result run(Path in, Path out) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int rc = Cbact04c.run(in, out, "2022071800", () -> CLOCK, new PrintStream(o, true, StandardCharsets.ISO_8859_1),
            new PrintStream(e, true, StandardCharsets.ISO_8859_1));
        return new Result(rc, o.toString(StandardCharsets.ISO_8859_1), e.toString(StandardCharsets.ISO_8859_1), out);
    }

    static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    /** TCATBAL record: acct(11) type(2) cat(4) bal zoned(11) + filler to 50. */
    static String tcat(String acct, String type, String cat, String balZoned) {
        return pad(acct + type + cat + balZoned, 50);
    }

    static String disc(String group, String type, String cat, String rateZoned) {
        return pad(pad(group, 10) + type + cat + rateZoned, 50);
    }

    static String lineWith(String rec, int col0, String v) {
        return rec.substring(0, col0) + v + rec.substring(col0 + v.length());
    }
}
