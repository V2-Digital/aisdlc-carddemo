package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test helpers: repo-relative paths and a run wrapper with the frozen PARM/clock. */
final class Support {
    static final Path ROOT = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
    static final Path SAMPLE = ROOT.resolve("app/data/ASCII");
    static final Path GOLDEN = ROOT.resolve("legacy/golden");

    record Result(int rc, Path dir, List<String> sysout) {
        Path transact() { return dir.resolve("TRANSACT.txt"); }
        Path acct() { return dir.resolve("ACCTFILE.txt"); }
        Path sysoutFile() { return dir.resolve("SYSOUT.txt"); }
    }

    static {
        if (!Files.isDirectory(GOLDEN) || !Files.isDirectory(SAMPLE)) {
            throw new IllegalStateException("repo root not found from user.dir; expected " + GOLDEN);
        }
    }

    /** Runs with explicit input files. */
    static Result run(Path tcat, Path xref, Path disc, Path acct, Path out) throws IOException {
        Files.createDirectories(out);
        Sysout so = new Sysout(new PrintStream(PrintStream.nullOutputStream()));
        Cbact04c.Files_ f = new Cbact04c.Files_(tcat, xref, disc, acct, out.resolve("ACCTFILE.txt"),
                out.resolve("TRANSACT.txt"));
        int rc = new Cbact04c(f, "2022071800", Clock.of("2022071800000000"), so).run();
        so.writeTo(out.resolve("SYSOUT.txt"));
        Files.writeString(out.resolve("RC.txt"), rc + "\n");
        return new Result(rc, out, so.lines());
    }

    static Result stock(Path out) throws IOException {
        return run(SAMPLE.resolve("tcatbal.txt"), SAMPLE.resolve("cardxref.txt"), SAMPLE.resolve("discgrp.txt"),
                SAMPLE.resolve("acctdata.txt"), out);
    }

    static Result afterPosting(Path out) throws IOException {
        Path g = GOLDEN.resolve("CBTRN02C");
        return run(g.resolve("TCATBALF.txt"), SAMPLE.resolve("cardxref.txt"), SAMPLE.resolve("discgrp.txt"),
                g.resolve("ACCTFILE.txt"), out);
    }

    static List<String> lines(Path p) throws IOException {
        return Files.readAllLines(p, StandardCharsets.ISO_8859_1);
    }

    static Path write(Path p, List<String> lines) throws IOException {
        Files.write(p, (lines.isEmpty() ? "" : String.join("\n", lines) + "\n").getBytes(StandardCharsets.ISO_8859_1));
        return p;
    }
}
