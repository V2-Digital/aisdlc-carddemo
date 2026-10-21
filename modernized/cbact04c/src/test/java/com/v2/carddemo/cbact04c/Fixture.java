package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Test helper: runs the program in a temp dir and exposes outputs. */
final class Fixture {
    static final Path ROOT = Path.of("").toAbsolutePath().resolve("../..").normalize();
    static final Path ASCII = ROOT.resolve("app/data/ASCII");
    static final Path GOLDEN = ROOT.resolve("legacy/golden");
    static final String CLOCK = "2022071800000000";

    final Path dir;
    int rc;
    String sysout;

    Fixture(Path dir) { this.dir = dir; }

    Path file(String dd) { return dir.resolve(dd + ".txt"); }

    void put(String dd, String content) throws IOException {
        Files.writeString(file(dd), content, StandardCharsets.ISO_8859_1);
    }

    void copy(String dd, Path src) throws IOException {
        Files.copy(src, file(dd));
    }

    /** Stock inputs, with optional overrides for ACCTFILE/TCATBALF from another dir. */
    void stock(Path overrideDir) throws IOException {
        copy("XREFFILE", ASCII.resolve("cardxref.txt"));
        copy("DISCGRP", ASCII.resolve("discgrp.txt"));
        copy("TCATBALF", overrideDir == null ? ASCII.resolve("tcatbal.txt") : overrideDir.resolve("TCATBALF.txt"));
        copy("ACCTFILE", overrideDir == null ? ASCII.resolve("acctdata.txt") : overrideDir.resolve("ACCTFILE.txt"));
    }

    void run() { run("2022071800", CLOCK); }

    void run(String parm, String clock) {
        Map<String, Path> dd = new HashMap<>();
        for (String n : new String[] {"TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE", "TRANSACT"}) {
            dd.put(n, file(n));
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        rc = new Cbact04c(dd, parm, () -> clock, new PrintStream(buf, true, StandardCharsets.ISO_8859_1)).run();
        sysout = buf.toString(StandardCharsets.ISO_8859_1);
    }

    String read(String dd) throws IOException {
        return Files.readString(file(dd), StandardCharsets.ISO_8859_1);
    }

    static String slurp(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.ISO_8859_1);
    }
}
