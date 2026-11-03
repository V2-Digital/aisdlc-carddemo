package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Test fixtures: repo root lookup, input staging (same DD mapping as legacy/harness/run.sh), run, compare. */
final class Fx {
    private Fx() {}

    static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("legacy/golden"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root with legacy/golden not found");
        }
        return p;
    }

    static Path golden(String scenario) {
        return root().resolve("legacy/golden").resolve(scenario);
    }

    static void stage(Path dir, boolean afterTrn02) throws IOException {
        Path d = root().resolve("app/data/ASCII");
        Files.copy(d.resolve("tcatbal.txt"), dir.resolve("TCATBALF.txt"));
        Files.copy(d.resolve("cardxref.txt"), dir.resolve("XREFFILE.txt"));
        Files.copy(d.resolve("acctdata.txt"), dir.resolve("ACCTFILE.txt"));
        Files.copy(d.resolve("discgrp.txt"), dir.resolve("DISCGRP.txt"));
        if (afterTrn02) {
            for (String f : new String[] {"ACCTFILE", "TCATBALF"}) {
                Files.copy(golden("CBTRN02C").resolve(f + ".txt"), dir.resolve(f + ".txt"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static int run(Path in, Path out) throws IOException {
        Files.createDirectories(out);
        return new InterestCalc(in, out, "2022071800", CLOCK, null).run();
    }

    static void assertSame(Path expectedDir, Path actualDir, String... files) throws IOException {
        for (String f : files) {
            Path e = expectedDir.resolve(f + ".txt");
            Path a = actualDir.resolve(f + ".txt");
            assertEquals(-1L, Files.mismatch(e, a), f + " differs from " + e);
        }
    }
}
