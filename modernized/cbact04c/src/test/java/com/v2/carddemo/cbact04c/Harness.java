package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Runs {@link Main} on stock data with optional per-DD overrides; paths resolve from the Maven basedir. */
final class Harness {
    static final Path ROOT = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().resolve("../..").normalize();
    static final Path STOCK = ROOT.resolve("app/data/ASCII");
    static final Path GOLDEN = ROOT.resolve("legacy/golden");
    static final Path CASES = ROOT.resolve("legacy/tests/cbact04c");

    record Result(int rc, String sysout, String transact, String acctfile) {
    }

    static Result run(Path inputDir, String clock) throws IOException {
        if (!Files.isDirectory(STOCK)) {
            throw new IllegalStateException("repo root not found from basedir: " + ROOT);
        }
        Path out = Files.createTempDirectory("cbact04c");
        Map<String, String> stock = Map.of("TCATBALF", "tcatbal.txt", "XREFFILE", "cardxref.txt",
                "DISCGRP", "discgrp.txt", "ACCTFILE", "acctdata.txt");
        String[] args = new String[stock.size() + 4];
        int i = 0;
        args[i++] = "2022071800";
        for (var e : stock.entrySet()) {
            Path p = inputDir != null && Files.exists(inputDir.resolve(e.getKey() + ".txt"))
                    ? inputDir.resolve(e.getKey() + ".txt") : STOCK.resolve(e.getValue());
            args[i++] = "--" + e.getKey() + "=" + p;
        }
        args[i++] = "--ACCTFILE-OUT=" + out.resolve("ACCTFILE.txt");
        args[i++] = "--TRANSACT=" + out.resolve("TRANSACT.txt");
        args[i] = "--clock=" + clock;
        ByteArrayOutputStream sys = new ByteArrayOutputStream();
        int rc = Main.run(args, Map.of(), new PrintStream(sys, true, StandardCharsets.ISO_8859_1),
                new PrintStream(new ByteArrayOutputStream()));
        return new Result(rc, sys.toString(StandardCharsets.ISO_8859_1), read(out.resolve("TRANSACT.txt")),
                read(out.resolve("ACCTFILE.txt")));
    }

    static String read(Path p) throws IOException {
        return Files.exists(p) ? new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1) : null;
    }
}
