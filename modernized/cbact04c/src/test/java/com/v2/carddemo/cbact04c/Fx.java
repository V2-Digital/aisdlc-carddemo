package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test helpers: repo paths, in-process runs, synthetic fixed-width records. */
final class Fx {
    static final Path ROOT = Path.of(System.getProperty("repo.root", "../..")).normalize();
    static final Path DATA = ROOT.resolve("app/data/ASCII");
    static final Path GOLDEN = ROOT.resolve("legacy/golden");
    static final String NOW = "2022071800000000";

    record Result(int rc, List<String> sysout, String err, Path out) {}

    static Result run(Path in, Path out, String now) throws Exception {
        ByteArrayOutputStream so = new ByteArrayOutputStream(), se = new ByteArrayOutputStream();
        int rc = new Cbact04c(in, out, "2022071800", Clock.fixed(now),
                new PrintStream(so, true, StandardCharsets.ISO_8859_1),
                new PrintStream(se, true, StandardCharsets.ISO_8859_1)).run();
        String s = so.toString(StandardCharsets.ISO_8859_1);
        return new Result(rc, s.isEmpty() ? List.of() : List.of(s.split("\n", -1)).subList(0, s.split("\n", -1).length - 1),
                se.toString(StandardCharsets.ISO_8859_1), out);
    }

    static String read(Path p) throws Exception { return Files.readString(p, StandardCharsets.ISO_8859_1); }

    static List<String> lines(Path p) throws Exception { return Files.readAllLines(p, StandardCharsets.ISO_8859_1); }

    static Path stage(Path dir, Path tcatbal, Path acct) throws Exception {
        Files.createDirectories(dir);
        Files.copy(tcatbal, dir.resolve("TCATBALF.txt"));
        Files.copy(acct, dir.resolve("ACCTFILE.txt"));
        Files.copy(DATA.resolve("cardxref.txt"), dir.resolve("XREFFILE.txt"));
        Files.copy(DATA.resolve("discgrp.txt"), dir.resolve("DISCGRP.txt"));
        return dir;
    }

    static String pad(String s, int n) { return s + " ".repeat(n - s.length()); }

    /** TCATBAL record: acct(11) type(2) cat(4) bal S9(9)V99 as 11 chars (pre-encoded) + 22 filler. */
    static String tcat(String acct, String type, String cat, String balZoned) {
        return acct + type + cat + balZoned + " ".repeat(22);
    }

    /** Minimal synthetic input set; one account (id 00000000001, blank group), DEFAULT rates as given. */
    static Path synth(Path dir, List<String> tcat, List<String> disc, boolean xref, boolean acct) throws Exception {
        Files.createDirectories(dir);
        String a = lines(DATA.resolve("acctdata.txt")).get(0);
        Files.writeString(dir.resolve("TCATBALF.txt"), String.join("\n", tcat) + "\n", StandardCharsets.ISO_8859_1);
        Files.writeString(dir.resolve("DISCGRP.txt"), String.join("\n", disc) + (disc.isEmpty() ? "" : "\n"), StandardCharsets.ISO_8859_1);
        Files.writeString(dir.resolve("XREFFILE.txt"), xref ? lines(DATA.resolve("cardxref.txt")).stream().filter(l -> l.substring(25, 36).equals("00000000001")).findFirst().orElseThrow() + "\n" : "");
        Files.writeString(dir.resolve("ACCTFILE.txt"), acct ? a + "\n" : "");
        return dir;
    }

    static String disc(String group, String type, String cat, String rateZoned) {
        return pad(group, 10) + type + cat + rateZoned + " ".repeat(28);
    }
}
