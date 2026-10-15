package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Test helpers: repo paths, input staging, running the program, synthetic record builders. */
final class Support {
    static final String CLOCK = "2022071800000000";
    static final String PARM = "2022071800";

    private Support() {}

    static Path repoRoot() {
        Path root = Path.of("").toAbsolutePath().resolve("../..").normalize();
        if (!Files.isDirectory(root.resolve("legacy/golden"))) {
            throw new IllegalStateException("repo root not found at " + root + " (run mvn from modernized/cbact04c)");
        }
        return root;
    }

    record Result(int rc, String sysout, Path out) {
        String file(String name) throws IOException {
            return Files.readString(out.resolve(name), StandardCharsets.ISO_8859_1);
        }
    }

    static Result run(Path in, Path out) throws IOException {
        Files.createDirectories(out);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int rc = new InterestCalc(PARM, CLOCK, new PrintStream(buf, true, StandardCharsets.ISO_8859_1)).run(in, out);
        return new Result(rc, buf.toString(StandardCharsets.ISO_8859_1), out);
    }

    static void put(Path dir, String dd, String... records) throws IOException {
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.writeString(dir.resolve(dd + ".txt"), sb.toString(), StandardCharsets.ISO_8859_1);
    }

    static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + bal, 50);
    }

    static String xref(String card, String acct) {
        return pad(card + "000000000" + acct, 50);
    }

    /** Account record: balance and group id set, cycle credit/debit given, rest spaces. */
    static String acct(String id, String bal, String group, String cycCredit, String cycDebit) {
        StringBuilder sb = new StringBuilder(" ".repeat(300));
        sb.replace(0, 11, id).replace(11, 12, "Y").replace(12, 24, bal);
        sb.replace(78, 90, cycCredit).replace(90, 102, cycDebit).replace(112, 122, pad(group, 10));
        return sb.toString();
    }

    static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + rate, 50);
    }

    static final String Z12 = "00000000000{";

    /** Single-account fixture: TCATBAL rows, plus a trailing dummy account so the account under test is rewritten. */
    static Path fixture(Path dir, Map<String, String[]> files) throws IOException {
        for (var e : files.entrySet()) {
            put(dir, e.getKey(), e.getValue());
        }
        return dir;
    }
}
