package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/** In-memory fixture builders and repo paths. */
final class Fx {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final LocalDateTime CLOCK = LocalDateTime.of(2022, 7, 18, 0, 0, 0);

    private Fx() {}

    static String tcat(String acct, String type, String cat, String bal) {
        return Records.pad(acct + type + cat + bal, 50);
    }

    static String xref(String card, String acct) {
        return Records.pad(card, 25) + acct;
    }

    /** Account record per CVACT01Y; money fields are 12-char zoned S9(10)V99. Group at 112, cycle credit/debit at 78/90. */
    static String acct(String id, String bal, String cr, String db, String group) {
        return Records.pad(id + "Y" + bal + "00000002000{" + "00000001000{" + "2014-11-20" + "2025-05-20"
                + "2025-05-20" + cr + db + "A000000000" + group, 300);
    }

    static String acct(String id, String group) {
        return acct(id, "00000001000{", "00000000500{", "00000000700{", group);
    }

    static String disc(String group, String type, String cat, String rate) {
        return Records.pad(Records.pad(group, 10) + type + cat + rate, 50);
    }

    static InterestCalc calc() {
        return new InterestCalc("2022071800", CLOCK, 0);
    }

    static InterestCalc.Result run(List<String> t, List<String> x, List<String> a, List<String> d) {
        return calc().run(t, x, a, d);
    }

    static List<String> lines(Path p, int lrecl) throws IOException {
        return Records.read(p, lrecl);
    }

    static String amount(String tran) {
        return tran.substring(132, 143);
    }

    /** Runs Main and returns {rc, stdout}. */
    static Object[] cli(String... args) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int rc = Main.run(args, new PrintStream(b, true, StandardCharsets.ISO_8859_1));
        return new Object[] {rc, b.toString(StandardCharsets.ISO_8859_1)};
    }

    static byte[] bytes(Path p) throws IOException {
        return Files.readAllBytes(p);
    }
}
