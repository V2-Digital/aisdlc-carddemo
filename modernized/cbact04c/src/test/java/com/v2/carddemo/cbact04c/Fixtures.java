package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Shared helpers: repo paths and tiny in-memory datasets. */
final class Fixtures {
    static final Path ROOT = Path.of(System.getProperty("repo.root", "../..")).toAbsolutePath().normalize();
    static final String PARM = "2022071800";
    static final Clock CLOCK = Clock.fixed("2022071800000000");

    private Fixtures() {}

    static Path golden(String scenario, String file) { return ROOT.resolve("legacy/golden").resolve(scenario).resolve(file); }

    static Path data(String file) { return ROOT.resolve("app/data/ASCII").resolve(file); }

    static byte[] bytes(Path p) throws IOException { return Files.readAllBytes(p); }

    static List<String> lines(Path p) throws IOException {
        String s = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
        List<String> l = new ArrayList<>(List.of(s.split("\n", -1)));
        if (!l.isEmpty() && l.get(l.size() - 1).isEmpty()) l.remove(l.size() - 1);
        return l;
    }

    static String pad(String s, int n) { return RecordFiles.pad(s, n); }

    static String tcat(String acct, String type, String cat, String bal) { return pad(acct + type + cat + bal, 50); }

    static String xref(String card, String acct) { return pad(pad(card, 25) + acct, 50); }

    static String disc(String group, String type, String cat, String rate) { return pad(pad(group, 10) + type + cat + rate, 50); }

    static String acct(String id, String bal, String group) {
        StringBuilder sb = new StringBuilder(pad(id + "Y" + bal, 300));
        sb.replace(78, 90, "000000000100").replace(90, 102, "00000000020}").replace(112, 122, pad(group, 10));
        return sb.toString();
    }

    static Path write(Path dir, String name, String... recs) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, (String.join("\n", recs) + "\n").getBytes(StandardCharsets.ISO_8859_1));
        return p;
    }

    record Result(int rc, List<String> sysout, Path out) {}

    static Result runMain(Path out, Path tcat, Path xref, Path acct, Path disc) throws Exception {
        java.io.PrintStream old = System.out, olderr = System.err;
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int rc;
        try {
            System.setOut(new java.io.PrintStream(buf, true, StandardCharsets.ISO_8859_1));
            System.setErr(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
            rc = Main.run(new String[] {"--parm", PARM, "--clock", "2022071800000000", "--out", out.toString(),
                "TCATBALF=" + tcat, "XREFFILE=" + xref, "ACCTFILE=" + acct, "DISCGRP=" + disc});
        } finally {
            System.setOut(old);
            System.setErr(olderr);
        }
        String s = buf.toString(StandardCharsets.ISO_8859_1);
        List<String> l = new ArrayList<>(List.of(s.split("\\R", -1)));
        if (!l.isEmpty() && l.get(l.size() - 1).isEmpty()) l.remove(l.size() - 1);
        return new Result(rc, l, out);
    }
}
