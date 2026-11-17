package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/** Test helpers: repo paths, fixture builders, and a run wrapper that captures streams. */
final class T {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path GOLDEN = ROOT.resolve("legacy/golden");
    static final Path ASCII = ROOT.resolve("app/data/ASCII");
    static final Path ABENDS = ROOT.resolve("legacy/tests/cbact04c-abends");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    record Result(int rc, String out, String err) {}

    static Result run(String parm, Path in, Path out) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int rc = Cbact04c.run(parm, in, out, CLOCK, new PrintStream(o, true, StandardCharsets.ISO_8859_1),
                new PrintStream(e, true, StandardCharsets.ISO_8859_1));
        return new Result(rc, o.toString(StandardCharsets.ISO_8859_1), e.toString(StandardCharsets.ISO_8859_1));
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return FixedFile.pad(acct + type + cat + Overpunch.encode(new BigDecimal(bal), 2, 11), 50);
    }

    static String acct(String id, String bal, String credit, String debit, String group) {
        String s = FixedFile.pad(id + "Y" + Overpunch.encode(new BigDecimal(bal), 2, 12), 78)
                + Overpunch.encode(new BigDecimal(credit), 2, 12) + Overpunch.encode(new BigDecimal(debit), 2, 12);
        return FixedFile.pad(FixedFile.pad(s, 112) + FixedFile.pad(group, 10), 300);
    }

    static String xref(String card, String acct) {
        return FixedFile.pad(FixedFile.pad(card, 25) + acct, 50);
    }

    static String disc(String group, String type, String cat, String rate) {
        return FixedFile.pad(FixedFile.pad(group, 10) + type + cat + Overpunch.encode(new BigDecimal(rate), 2, 6), 50);
    }

    static void put(Path dir, String dd, String... recs) throws IOException {
        FixedFile.write(dir.resolve(dd + ".txt"), List.of(recs));
    }

    static List<String> read(Path f, int lrecl) throws IOException {
        return FixedFile.read(f, lrecl);
    }

    static byte[] bytes(Path f) throws IOException {
        return Files.readAllBytes(f);
    }
}
