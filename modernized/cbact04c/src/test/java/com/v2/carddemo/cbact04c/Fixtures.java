package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Staging helpers shared by the tests. */
final class Fixtures {
    static final Path ROOT = Path.of(System.getProperty("repo.root", "../..")).toAbsolutePath().normalize();
    static final String NOW = "2022071800000000";

    private Fixtures() {}

    static Path golden(String scenario, String file) {
        return ROOT.resolve("legacy/golden").resolve(scenario).resolve(file);
    }

    /** Stock inputs under harness DD names; optionally overlay ACCTFILE/TCATBALF from CBTRN02C. */
    static Path stageInputs(Path dir, boolean afterCbtrn02c) throws IOException {
        Path a = ROOT.resolve("app/data/ASCII");
        Files.createDirectories(dir);
        Files.copy(a.resolve("tcatbal.txt"), dir.resolve("TCATBALF.txt"));
        Files.copy(a.resolve("cardxref.txt"), dir.resolve("XREFFILE.txt"));
        Files.copy(a.resolve("acctdata.txt"), dir.resolve("ACCTFILE.txt"));
        Files.copy(a.resolve("discgrp.txt"), dir.resolve("DISCGRP.txt"));
        if (afterCbtrn02c) {
            Files.copy(golden("CBTRN02C", "ACCTFILE.txt"), dir.resolve("ACCTFILE.txt"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(golden("CBTRN02C", "TCATBALF.txt"), dir.resolve("TCATBALF.txt"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return dir;
    }

    static String sp(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return sp(acct + type + cat + bal, 50);
    }

    /** 300-byte account: id, balance (cols 13-24), cycle credit/debit (79-102), group (113-122). */
    static String acct(String id, String bal, String credit, String debit, String group) {
        StringBuilder sb = new StringBuilder(" ".repeat(300));
        sb.replace(0, 11, id).replace(11, 12, "Y").replace(12, 24, bal)
          .replace(78, 90, credit).replace(90, 102, debit).replace(112, 122, sp(group, 10));
        return sb.toString();
    }

    static String xref(String card, String acct) {
        return sp(card + "000000000" + acct, 50);
    }

    static String disc(String group, String type, String cat, String rate) {
        return sp(sp(group, 10) + type + cat + rate, 50);
    }

    static void lines(Path f, List<String> l) throws IOException {
        Files.writeString(f, String.join("\n", l) + (l.isEmpty() ? "" : "\n"), StandardCharsets.ISO_8859_1);
    }

    static List<String> read(Path f) throws IOException {
        return Files.readAllLines(f, StandardCharsets.ISO_8859_1);
    }
}
