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

/** Fixture builder + runner. Accounts: 2 balance rows per call set; helper strings are fixed width. */
final class Fx {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);
    final Path dir;
    String out = "";
    String err = "";
    int rc;

    Fx(Path dir) { this.dir = dir; }

    static String pad(String s, int n) { return (s + " ".repeat(n)).substring(0, n); }
    static String acct(String id, String bal, String group) {
        StringBuilder sb = new StringBuilder(pad(id + "Y" + bal, 300));
        sb.replace(78, 102, "00000000100A00000000200B");
        sb.replace(112, 122, pad(group, 10));
        return sb.toString();
    }
    static String cat(String acct, String type, String cat, String bal) { return pad(acct + type + cat + bal, 50); }
    static String xref(String card, String acct) { return pad(card, 25) + acct + pad("", 14); }
    static String disc(String group, String type, String cat, String rate) { return pad(pad(group, 10) + type + cat + rate, 50); }

    Path w(String name, List<String> rows) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, rows.isEmpty() ? "" : String.join("\n", rows) + "\n", StandardCharsets.ISO_8859_1);
        return p;
    }

    Fx run(String parm, List<String> bal, List<String> xr, List<String> acc, List<String> dis) throws IOException {
        return run(parm, w("tcat", bal), w("xref", xr), w("acct", acc), w("disc", dis));
    }

    Fx run(String parm, Path bal, Path xr, Path acc, Path dis) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
        rc = new Cbact04c(bal, xr, acc, dis, dir.resolve("tran"), CLOCK,
                new PrintStream(o, true, StandardCharsets.ISO_8859_1), new PrintStream(e, true, StandardCharsets.ISO_8859_1))
                .run(parm);
        out = o.toString(StandardCharsets.ISO_8859_1);
        err = e.toString(StandardCharsets.ISO_8859_1);
        return this;
    }

    List<String> lines(String name) throws IOException {
        String t = Files.readString(dir.resolve(name), StandardCharsets.ISO_8859_1);
        return t.isEmpty() ? List.of() : List.of(t.split("\n", -1)).subList(0, t.split("\n", -1).length - 1);
    }
    List<String> tran() throws IOException { return lines("tran"); }
    List<String> acct() throws IOException { return lines("acct"); }
}
