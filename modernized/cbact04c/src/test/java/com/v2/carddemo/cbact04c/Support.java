package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class Support {
    static final Path BASE = Path.of(System.getProperty("basedir", ".")).toAbsolutePath();
    static final Path GOLDEN = BASE.resolve("../../legacy/golden").normalize();
    static final Path ASCII = BASE.resolve("../../app/data/ASCII").normalize();
    static final String TS = "2022-07-18-00.00.00.000000";

    record Result(int rc, String stdout, Path out) {}

    static Result run(Path tcat, Path xref, Path disc, Path acct, Path outDir) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos, false, StandardCharsets.ISO_8859_1);
        int rc = Cbact04c.run(tcat, xref, disc, acct, outDir, "2022071800", TS, ps);
        ps.flush();
        return new Result(rc, bos.toString(StandardCharsets.ISO_8859_1), outDir);
    }

    static byte[] bytes(Path p) throws IOException {
        return Files.readAllBytes(p);
    }

    static String text(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.ISO_8859_1);
    }

    static List<String> lines(Path p) throws IOException {
        return FixedRecords.read(p, 400).stream().map(String::stripTrailing).toList();
    }

    /** 300-byte account record: id, curr-bal at 12, group id at 112. */
    static String acct(String id, String bal, String group) {
        StringBuilder s = new StringBuilder(FixedRecords.fit(id, 12));
        s.append(bal);
        s = new StringBuilder(FixedRecords.fit(s.toString(), 78));
        s.append("00000000000{").append("00000000000{");
        s = new StringBuilder(FixedRecords.fit(s.toString(), 112));
        s.append(FixedRecords.fit(group, 10));
        return FixedRecords.fit(s.toString(), 300);
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return FixedRecords.fit(acct + type + cat + bal, 50);
    }

    static String xref(String card, String acct) {
        return FixedRecords.fit(FixedRecords.fit(card, 25) + acct, 50);
    }

    static String disc(String key16, String rate6) {
        return FixedRecords.fit(key16 + rate6, 50);
    }

    static Path write(Path dir, String name, String... recs) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, String.join("\n", recs) + (recs.length > 0 ? "\n" : ""), StandardCharsets.ISO_8859_1);
        return p;
    }
}
