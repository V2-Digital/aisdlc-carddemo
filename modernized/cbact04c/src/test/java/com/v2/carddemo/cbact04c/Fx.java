package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test helper: builds fixed-width records, runs the job in a temp dir, reads outputs. */
final class Fx {
    static final String PARM = "2022071800";
    static final String CLOCK = "2022071800000000";
    static final Path ROOT = findRoot();

    final Map<String, Path> in = new HashMap<>();
    final Path out;
    int rc;

    Fx(Path tmp) {
        out = tmp.resolve("out");
    }

    static Path findRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("legacy/golden"))) {
            p = p.getParent();
        }
        return p;
    }

    static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + bal, 50);
    }

    static String xref(String card, String acct) {
        return pad(pad(card, 25) + acct, 50);
    }

    /** bal/credit/debit are 12-char zoned fields. */
    static String acct(String id, String bal, String credit, String debit, String group) {
        String r = id + "Y" + bal + "00000020200{" + "00000010200{" + "2014-11-20" + "2025-05-20" + "2025-05-20"
                + credit + debit + "ZIP-1     " + pad(group, 10);
        return pad(r, 300);
    }

    static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + rate, 50);
    }

    Fx dd(String name, String... lines) throws IOException {
        Path p = out.getParent().resolve(name + ".txt");
        Files.write(p, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.ISO_8859_1));
        in.put(name, p);
        return this;
    }

    Fx run() throws IOException {
        rc = Cbact04c.run(in, out, PARM, () -> CLOCK);
        return this;
    }

    List<String> lines(String name) throws IOException {
        return Files.readAllLines(out.resolve(name + ".txt"), StandardCharsets.ISO_8859_1);
    }

    String text(String name) throws IOException {
        return Files.readString(out.resolve(name + ".txt"), StandardCharsets.ISO_8859_1);
    }
}
