package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Test fixtures: record builders and a runner capturing SYSOUT, RC and output files. */
final class Fx {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final String NOW = "2022071800000000";

    record Result(int rc, List<String> sysout, Path out) {
        String file(String name) throws IOException {
            return new String(Files.readAllBytes(out.resolve(name)), StandardCharsets.ISO_8859_1);
        }
        List<String> records(String name) throws IOException {
            String t = file(name);
            return t.isEmpty() ? List.of() : List.of(t.split("\n", -1)).subList(0, t.split("\n", -1).length - 1);
        }
    }

    static String pad(String s, int n) { return s + " ".repeat(n - s.length()); }
    static String bal(String v, int w) { return Zoned.encode(new BigDecimal(v), w); }

    static String tcat(String acct, String type, String cat, String balance) {
        return pad(acct + type + cat + bal(balance, 11), 50);
    }
    static String acct(String id, String curBal, String credit, String debit, String group) {
        String r = id + "Y" + bal(curBal, 12) + pad("", 54) + bal(credit, 12) + bal(debit, 12)
                + group + pad("", 10);
        return pad(r.substring(0, 112) + pad(group, 10), 300);
    }
    static String xref(String card, String acct) { return pad(card + "000000001" + acct, 50); }
    static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + bal(rate, 6), 50);
    }

    static Path dir(Path base, String name, List<String> tcat, List<String> xref, List<String> disc,
                    List<String> acct) throws IOException {
        Path d = Files.createDirectories(base.resolve(name));
        put(d, "TCATBALF", tcat); put(d, "XREFFILE", xref); put(d, "DISCGRP", disc); put(d, "ACCTFILE", acct);
        return d;
    }
    static void put(Path d, String dd, List<String> recs) throws IOException {
        StringBuilder sb = new StringBuilder();
        recs.forEach(r -> sb.append(r).append('\n'));
        Files.write(d.resolve(dd + ".txt"), sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    static Result run(Path in, Path out) throws IOException {
        List<String> sys = new ArrayList<>();
        Files.createDirectories(out);
        int rc = 0;
        try {
            new IntCalc("2022071800", NOW, sys::add).run(in, out);
        } catch (Abend a) {
            rc = 16;
        }
        return new Result(rc, sys, out);
    }

    /** Standard small world: accounts 1..n each with xref, DEFAULT 0100 type 01 cat 0001 rate 15.00. */
    static Result simple(Path tmp, List<String> tcat, List<String> accts, List<String> disc) throws IOException {
        List<String> xr = new ArrayList<>();
        for (String a : accts) xr.add(xref("4" + a.substring(0, 11) + "0000", a.substring(0, 11)));
        Path in = dir(tmp, "in", tcat, xr, disc, accts);
        return run(in, tmp.resolve("out"));
    }
}
