package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {
    @TempDir Path tmp;

    private ProcessBuilder pb(String tcat) {
        Path a = Support.root().resolve("app/data/ASCII");
        Path jar = Path.of("target/classes").toAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-cp", jar.toString(), "com.v2.carddemo.cbact04c.Main", "2022071800",
                "--TCATBALF=" + tcat, "--XREFFILE=" + a.resolve("cardxref.txt"),
                "--DISCGRP=" + a.resolve("discgrp.txt"), "--ACCTFILE=" + a.resolve("acctdata.txt"),
                "--out=" + tmp);
        pb.environment().put("CARDDEMO_CURRENT_DATE", Support.CLOCK);
        pb.redirectErrorStream(false);
        return pb;
    }

    @Test
    void runsAsSeparateProcessAndMatchesGolden() throws Exception {
        Path root = Support.root();
        Process p = pb(root.resolve("app/data/ASCII/tcatbal.txt").toString()).redirectOutput(tmp.resolve("sysout").toFile())
                .start();
        assertEquals(0, p.waitFor());
        Path g = root.resolve("legacy/golden/CBACT04C");
        assertEquals(Support.read(g.resolve("TRANSACT.txt")), Support.read(tmp.resolve("TRANSACT.txt")));
        assertEquals(Support.read(g.resolve("ACCTFILE.txt")), Support.read(tmp.resolve("ACCTFILE.txt")));
        assertEquals(Support.read(g.resolve("SYSOUT.txt")), Support.read(tmp.resolve("sysout")));
    }

    @Test
    void abendExitCode16AndStderr() throws Exception {
        Path t = Support.write(tmp, "bad.txt", "99999999999010001" + "0000010000{");
        Process p = pb(t.toString()).redirectOutput(tmp.resolve("sysout").toFile()).redirectError(tmp.resolve("err").toFile())
                .start();
        assertEquals(16, p.waitFor());
        assertTrue(Support.read(tmp.resolve("err")).contains("CEE3ABD: ABEND U0999"));
    }
}
