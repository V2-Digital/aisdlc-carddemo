package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/** Test helpers: repo paths and synthetic fixed-width inputs. */
final class Fx {
    private Fx() {}

    static final Supplier<LocalDateTime> CLOCK = () -> LocalDateTime.of(2022, 7, 18, 0, 0, 0);

    static Path repo() {
        Path p = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
        if (!Files.isDirectory(p.resolve("legacy/golden"))) {
            throw new IllegalStateException("legacy/golden not found under " + p + "; run mvn from modernized/cbact04c");
        }
        return p;
    }

    static String pad(String s, int n) { return s + " ".repeat(n - s.length()); }

    static String z(String v, int w) { return Zoned.encode(new BigDecimal(v), w); }

    static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + z(bal, 11), 50);
    }

    static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + z(rate, 6), 50);
    }

    static String xref(String card, String acct) { return pad(card + "000000001" + acct, 50); }

    static String acct(String id, String bal, String group) {
        return pad(id + "Y" + z(bal, 12) + z("1000", 12) + z("500", 12) + "2020-01-01" + "2030-01-01" + "2025-01-01"
                + z("5.00", 12) + z("7.00", 12) + pad("", 10) + pad(group, 10), 300);
    }

    static Path inputs(Path dir, List<String> tcat, List<String> xref, List<String> disc, List<String> acct)
            throws IOException {
        Files.write(dir.resolve("TCATBALF.txt"), tcat);
        Files.write(dir.resolve("XREFFILE.txt"), xref);
        Files.write(dir.resolve("DISCGRP.txt"), disc);
        Files.write(dir.resolve("ACCTFILE.txt"), acct);
        return dir;
    }

    static final String A1 = "00000000001", A2 = "00000000002", A3 = "00000000003";
    static final String C1 = "1111111111111111", C2 = "2222222222222222", C3 = "3333333333333333";
}
