package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Shared fixtures: after-CBTRN02C inputs, with optional replacement of single input files. */
final class Fx {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path GOLDEN = ROOT.resolve("legacy/golden");
    static final Path ASCII = ROOT.resolve("app/data/ASCII");

    static String pad50(String r) { return (r + " ".repeat(50)).substring(0, 50); }

    int rc;
    String sysout;
    Path out;

    static String s(Path p) throws Exception { return Files.readString(p, StandardCharsets.ISO_8859_1); }

    static void put(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name + ".txt"), content, StandardCharsets.ISO_8859_1);
    }

    /** Writes the four inputs (after-CBTRN02C) into in; overrides replace a file's content. */
    static void inputs(Path in, String... overrides) throws Exception {
        Path g = GOLDEN.resolve("CBTRN02C");
        Files.copy(g.resolve("ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(g.resolve("TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        for (int i = 0; i < overrides.length; i += 2) put(in, overrides[i], overrides[i + 1]);
    }

    static Fx run(Path in, Path out) throws Exception {
        Fx f = new Fx();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        f.rc = new InterestCalc("2022071800", () -> "2022071800000000", new PrintStream(buf, true, "ISO-8859-1"))
                .run(in, out);
        f.sysout = buf.toString(StandardCharsets.ISO_8859_1);
        f.out = out;
        return f;
    }

    static List<String> lines(Path p) throws Exception { return Files.readAllLines(p, StandardCharsets.ISO_8859_1); }
}
