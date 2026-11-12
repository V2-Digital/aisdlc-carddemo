package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

final class Support {
    private Support() {}

    static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    record Result(int rc, String sysout, String stderr, Path dir) {
        byte[] file(String dd) throws Exception {
            return Files.readAllBytes(dir.resolve(dd + ".txt"));
        }
        String text(String dd) throws Exception {
            return new String(file(dd), StandardCharsets.ISO_8859_1);
        }
    }

    static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("legacy/golden"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root not found");
        }
        return p;
    }

    static Path sample(String f) {
        return repoRoot().resolve("app/data/ASCII").resolve(f);
    }

    static Path golden(String scenario, String f) {
        return repoRoot().resolve("legacy/golden").resolve(scenario).resolve(f);
    }

    /** inputs: DD name -> source file. Copies them into dir, runs, returns outputs. */
    static Result run(Path dir, Map<String, Path> inputs, String parm) throws Exception {
        Map<String, Path> dd = new LinkedHashMap<>();
        for (var e : inputs.entrySet()) {
            Path t = dir.resolve(e.getKey() + ".txt");
            Files.copy(e.getValue(), t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dd.put(e.getKey(), t);
        }
        dd.put("TRANSACT", dir.resolve("TRANSACT.txt"));
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        int rc = new InterestCalc(dd, parm, CLOCK, new PrintStream(so, true, "ISO-8859-1"),
                new PrintStream(se, true, "ISO-8859-1")).run();
        return new Result(rc, so.toString("ISO-8859-1"), se.toString("ISO-8859-1"), dir);
    }

    static Map<String, Path> stock() {
        Map<String, Path> m = new LinkedHashMap<>();
        m.put("TCATBALF", sample("tcatbal.txt"));
        m.put("XREFFILE", sample("cardxref.txt"));
        m.put("ACCTFILE", sample("acctdata.txt"));
        m.put("DISCGRP", sample("discgrp.txt"));
        return m;
    }

    static String pad(String s, int n) {
        return FixedWidth.pad(s, n);
    }

    /** Fixture builders. */
    static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + bal, 50);
    }

    static String xref(String card, String acct) {
        return pad(card + " ".repeat(9) + acct, 50);
    }

    static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + rate, 50);
    }

    static String acct(String id, String bal, String group) {
        String r = id + "Y" + bal + "000000100000" + "000000050000" + "2020-01-01" + "2030-01-01" + "2025-01-01"
                + "00000000100{" + "00000000200{" + "12345     " + pad(group, 10);
        return pad(r, 300);
    }

    static Map<String, Path> fixture(Path dir, String tcat, String xref, String acct, String disc) throws Exception {
        Map<String, Path> m = new LinkedHashMap<>();
        m.put("TCATBALF", write(dir, "in_TCATBALF", tcat));
        m.put("XREFFILE", write(dir, "in_XREFFILE", xref));
        m.put("ACCTFILE", write(dir, "in_ACCTFILE", acct));
        m.put("DISCGRP", write(dir, "in_DISCGRP", disc));
        return m;
    }

    static Path write(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.ISO_8859_1);
        return p;
    }

    static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
