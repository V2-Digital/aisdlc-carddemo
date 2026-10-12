package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestSupport {
    private TestSupport() {}

    static final Path REPO = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
    static final Path GOLDEN = REPO.resolve("legacy/golden");
    static final Path SAMPLE = REPO.resolve("app/data/ASCII");
    static final String CLOCK = "2022071800000000";

    record Result(int rc, String sysout, String stderr) {}

    static Result run(Path dir, String parm, String clock, Path tcat, Path xref, Path disc, Path acct)
            throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
        int rc = Main.run(new String[] {"--parm", parm, "--tcatbalf", tcat.toString(), "--xreffile", xref.toString(),
                "--discgrp", disc.toString(), "--acctfile", acct.toString(), "--transact",
                dir.resolve("TRANSACT.txt").toString(), "--clock", clock},
                new PrintStream(o, true, StandardCharsets.ISO_8859_1), new PrintStream(e, true, StandardCharsets.ISO_8859_1));
        return new Result(rc, o.toString(StandardCharsets.ISO_8859_1), e.toString(StandardCharsets.ISO_8859_1));
    }

    /** Copies src to dir (so the in-place ACCTFILE rewrite never touches the repo). */
    static Path copy(Path src, Path dir, String name) throws IOException {
        Path d = dir.resolve(name);
        Files.copy(src, d, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return d;
    }

    static void assertBytes(Path expected, Path actual, String label) throws IOException {
        byte[] e = Files.readAllBytes(expected), a = Files.readAllBytes(actual);
        assertBytes(e, a, label);
    }

    static void assertBytes(byte[] e, byte[] a, String label) {
        int n = Math.min(e.length, a.length);
        for (int i = 0; i < n; i++) {
            if (e[i] != a[i]) fail(label + ": first diff at byte " + i + " (record " + (i / 351 + 1) + ")");
        }
        if (e.length != a.length) fail(label + ": length " + a.length + " != expected " + e.length);
    }

    static long lines(Path p) throws IOException {
        return Files.readAllLines(p, StandardCharsets.ISO_8859_1).size();
    }
}
