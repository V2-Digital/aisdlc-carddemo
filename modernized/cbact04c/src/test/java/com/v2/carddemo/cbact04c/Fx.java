package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Test fixture: synthetic DD files in a temp dir and a runner. */
final class Fx {
    static final Clock CLOCK = Clock.fixed(
            LocalDateTime.parse("2022-07-18T00:00:00").toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    final Path dir;
    final Map<String, Path> dd = new TreeMap<>();
    String sysout;
    int rc;

    Fx(Path dir) {
        this.dir = dir;
        for (String n : Cbact04c.DDS) {
            dd.put(n, dir.resolve(n + ".txt"));
        }
    }

    static Path repo() {
        return Path.of(System.getProperty("basedir", ".")).resolve("../..").normalize();
    }

    static String acct(String id, String bal, String group) {
        StringBuilder sb = new StringBuilder(" ".repeat(300));
        sb.replace(0, 11, id).replace(11, 12, "Y").replace(12, 24, bal);
        sb.replace(78, 90, "00000000999{").replace(90, 102, "00000000888{").replace(112, 122, FixedFile.pad(group, 10));
        return sb.toString();
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return FixedFile.pad(acct + type + cat + bal, 50);
    }

    static String xref(String card, String acct) {
        return FixedFile.pad(card + " ".repeat(9) + acct, 50);
    }

    /** discgrp record: group(10) type(2) cat(4) rate(6). */
    static String disc(String group, String type, String cat, String rate) {
        return FixedFile.pad(FixedFile.pad(group, 10) + type + cat + rate, 50);
    }

    void write(String ddName, List<String> recs) throws Exception {
        FixedFile.write(dd.get(ddName), recs);
    }

    List<String> read(String ddName, int lrecl) throws Exception {
        return FixedFile.read(dd.get(ddName), lrecl);
    }

    int run(String parm) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        rc = Cbact04c.run(parm, dd, CLOCK, new PrintStream(b, true, StandardCharsets.ISO_8859_1));
        sysout = b.toString(StandardCharsets.ISO_8859_1);
        return rc;
    }

    /** One account 00000000001 (bal 100.00, group G), one tcat row, DEFAULT rate; returns the Fx. */
    static Fx simple(Path dir, String group, String balTcat, String rate, String defRate) throws Exception {
        Fx f = new Fx(dir);
        f.write("ACCTFILE", List.of(acct("00000000001", "00000010000{", group)));
        f.write("TCATBALF", List.of(tcat("00000000001", "01", "0001", balTcat)));
        f.write("XREFFILE", List.of(xref("4111111111111111", "00000000001")));
        f.write("DISCGRP", List.of(disc("DEFAULT", "01", "0001", defRate)));
        Files.writeString(f.dd.get("TRANSACT"), "");
        return f;
    }

    static String amountField(String tx) {
        return tx.substring(132, 143);
    }
}
