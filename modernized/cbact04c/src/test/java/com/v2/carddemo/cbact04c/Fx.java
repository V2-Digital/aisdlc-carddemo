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
import java.util.List;
import java.util.Map;

/** Test fixture: stages inputs as {@code <DD>.txt}, runs the job with a fixed clock. */
final class Fx {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path ASCII = ROOT.resolve("app/data/ASCII");
    static final Path GOLDEN = ROOT.resolve("legacy/golden");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    record Result(int rc, String sysout, String stderr, Path out) {
        List<String> lines(String file) throws IOException {
            return Files.readAllLines(out.resolve(file), StandardCharsets.ISO_8859_1);
        }
    }

    static Path stock(String dd) {
        return ASCII.resolve(switch (dd) {
            case "TCATBALF" -> "tcatbal.txt";
            case "XREFFILE" -> "cardxref.txt";
            case "ACCTFILE" -> "acctdata.txt";
            default -> "discgrp.txt";
        });
    }

    /** Stock inputs, with per-DD overrides (path of a file to copy, or literal content via {@code text}). */
    static Result run(Path tmp, Map<String, Path> files, Map<String, String> text) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path out = Files.createDirectories(tmp.resolve("out"));
        for (String dd : List.of("TCATBALF", "XREFFILE", "ACCTFILE", "DISCGRP")) {
            Path dst = in.resolve(dd + ".txt");
            if (text.containsKey(dd)) Files.writeString(dst, text.get(dd), StandardCharsets.ISO_8859_1);
            else Files.copy(files.getOrDefault(dd, stock(dd)), dst);
        }
        return exec(in, out);
    }

    static Result exec(Path in, Path out) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
        int rc = Cbact04c.run("2022071800", in, out, CLOCK,
                new PrintStream(o, true, StandardCharsets.ISO_8859_1), new PrintStream(e, true, StandardCharsets.ISO_8859_1));
        return new Result(rc, o.toString(StandardCharsets.ISO_8859_1), e.toString(StandardCharsets.ISO_8859_1), out);
    }

    static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    /** TCATBALF record: acct, type, cat, balance (11 zoned chars). */
    static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + bal, 50) + "\n";
    }

    static String bytes(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.ISO_8859_1);
    }
}
