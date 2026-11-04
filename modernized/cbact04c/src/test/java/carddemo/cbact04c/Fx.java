package carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Test helpers: fixture records, staging and in-process runs. */
final class Fx {
    private Fx() {}

    static final Path ROOT = Path.of("..", "..").toAbsolutePath().normalize();
    static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), java.time.ZoneOffset.UTC);

    record Result(int rc, String stdout, String stderr, Path out) {}

    static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    static String acct(String id, String bal, String credit, String debit, String group) {
        String r = pad(id + "Y" + bal, 78) + credit + debit;
        r = pad(r, 112) + pad(group, 10);
        return pad(r, 300);
    }

    static String xref(String card, String acct) {
        return pad(card + "000000001" + acct, 50);
    }

    static String disc(String group, String typeCat, String rate) {
        return pad(pad(group, 10) + typeCat + rate, 50);
    }

    static String bal(String acct, String typeCat, String amt) {
        return pad(acct + typeCat + amt, 50);
    }

    static Path stage(Path dir, List<String> tcatbal, List<String> xref, List<String> acct, List<String> disc)
            throws IOException {
        Files.createDirectories(dir);
        w(dir, "TCATBALF", tcatbal);
        w(dir, "XREFFILE", xref);
        w(dir, "ACCTFILE", acct);
        w(dir, "DISCGRP", disc);
        return dir;
    }

    static void w(Path dir, String dd, List<String> recs) throws IOException {
        Files.writeString(dir.resolve(dd + ".txt"), String.join("", recs.stream().map(s -> s + "\n").toList()),
                StandardCharsets.ISO_8859_1);
    }

    static Result run(Path in, Path out) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int rc = new Cbact04c("2022071800", CLOCK, new PrintStream(o, true, StandardCharsets.ISO_8859_1),
                new PrintStream(e, true, StandardCharsets.ISO_8859_1)).run(in, out);
        return new Result(rc, o.toString(StandardCharsets.ISO_8859_1), e.toString(StandardCharsets.ISO_8859_1), out);
    }

    static List<String> lines(Path f) throws IOException {
        return Files.readAllLines(f, StandardCharsets.ISO_8859_1);
    }
}
