package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/** Test fixtures: small in-memory datasets written as canonical fixed-width text. */
final class Fx {
    static final Clock CLOCK = Main.fixed("2022071800000000");

    static Path repo() { return Path.of(System.getProperty("basedir", ".")).resolve("../..").normalize(); }

    static String tcat(String acct, String type, String cat, String bal) {
        return RecordFiles.pad(acct + type + cat + Zoned.encode(new BigDecimal(bal), 11, 2), 50);
    }
    static String xref(String card, String acct) {
        return RecordFiles.pad(card + " ".repeat(9) + acct, 50);
    }
    static String disc(String group, String type, String cat, String rate) {
        return RecordFiles.pad(RecordFiles.pad(group, 10) + type + cat + Zoned.encode(new BigDecimal(rate), 6, 2), 50);
    }
    static String acct(String id, String bal, String group) {
        String z = Zoned.encode(BigDecimal.ZERO, 12, 2);
        String r = id + "Y" + Zoned.encode(new BigDecimal(bal), 12, 2) + " ".repeat(24) + " ".repeat(30)
                + "00000000099{" + "00000000077{" + " ".repeat(10) + RecordFiles.pad(group, 10);
        return RecordFiles.pad(r, 300);
    }

    /** Writes the four inputs; returns the data dir. */
    static Path data(Path dir, List<String> tcat, List<String> xref, List<String> disc, List<String> acct) throws IOException {
        Files.createDirectories(dir);
        w(dir, "tcatbal.txt", tcat);
        w(dir, "cardxref.txt", xref);
        w(dir, "discgrp.txt", disc);
        w(dir, "acctdata.txt", acct);
        return dir;
    }
    static void w(Path dir, String n, List<String> l) throws IOException { RecordFiles.write(dir.resolve(n), l); }

    static String read(Path p) throws IOException { return new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1); }

    static InterestCalc calc(Path data) { return new InterestCalc(data, null, "2022071800", CLOCK); }
}
