package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test helpers: synthetic records and a CLI runner. */
final class Fx {
    static final Path ROOT = Path.of(System.getProperty("repo.root", "../..")).toAbsolutePath().normalize();
    static final String FROZEN = "2022071800000000";

    static String p(String s, int n) { return Datasets.pad(s, n); }

    static String tcat(String acct, String type, String cat, String bal) {
        return p(acct + type + cat + bal, 50);
    }

    static String xref(String acct, String card) { return p(card + "000000001" + acct, 50).substring(0, 50); }

    static String acct(String id, String bal, String group) {
        char[] r = new char[300];
        java.util.Arrays.fill(r, ' ');
        put(r, 0, id); put(r, 12, bal); put(r, 24, "000000500000{"); // limit-ish filler
        put(r, 78, "00000001000{"); put(r, 90, "00000000500}");
        put(r, 112, p(group, 10));
        return new String(r);
    }

    static String disc(String group, String type, String cat, String rate) { return p(p(group, 10) + type + cat + rate, 50); }

    private static void put(char[] r, int at, String s) { s.getChars(0, s.length(), r, at); }

    record Result(int rc, String sysout, String err, List<String> transact, List<String> accts) {}

    static Result run(Path dir, List<String> tcat, List<String> xref, List<String> disc, List<String> acct,
                      String parm, String clock) throws IOException {
        Map<String, String> env = new HashMap<>();
        env.put("DD_TCATBALF", w(dir, "TCATBALF", tcat));
        env.put("DD_XREFFILE", w(dir, "XREFFILE", xref));
        env.put("DD_DISCGRP", w(dir, "DISCGRP", disc));
        env.put("DD_ACCTFILE", w(dir, "ACCTFILE", acct));
        env.put("DD_TRANSACT", dir.resolve("TRANSACT").toString());
        env.put("CARDDEMO_CURRENT_DATE", clock);
        return exec(dir, env, parm);
    }

    static Result exec(Path dir, Map<String, String> env, String parm) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
        int rc = Main.run(parm, env, new PrintStream(o, true, StandardCharsets.ISO_8859_1),
                new PrintStream(e, true, StandardCharsets.ISO_8859_1));
        Path t = dir.resolve("TRANSACT"), a = dir.resolve("ACCTFILE");
        return new Result(rc, o.toString(StandardCharsets.ISO_8859_1), e.toString(StandardCharsets.ISO_8859_1),
                Files.exists(t) ? Datasets.read(t, 350) : List.of(),
                Files.exists(a) ? Datasets.read(a, 300) : List.of());
    }

    static String w(Path dir, String name, List<String> recs) throws IOException {
        Path f = dir.resolve(name);
        Datasets.write(f, recs);
        return f.toString();
    }
}
