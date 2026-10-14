package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefectTest {
    private static final Path G = Path.of("legacy/golden");

    /** D1: account 50 is never rewritten, yet its interest transaction is written. */
    @Test
    void d1_lastAccountNotRewritten_butItsTransactionIsWritten(@TempDir Path t) throws Exception {
        Path b = G.resolve("CBTRN02C");
        Path in = Files.createDirectory(t.resolve("in")), out = Files.createDirectory(t.resolve("out"));
        Files.copy(b.resolve("ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(b.resolve("TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(Path.of("app/data/ASCII/cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(Path.of("app/data/ASCII/discgrp.txt"), in.resolve("DISCGRP.txt"));
        int rc = Cbact04c.run("2022071800", new DdFiles(java.util.Map.of(), in, out), () -> "2022071800000000",
                new java.io.PrintStream(java.io.OutputStream.nullOutputStream()), System.err);
        assertEquals(0, rc);
        List<String> before = Records.read(in.resolve("ACCTFILE.txt"), 300);
        List<String> after = Records.read(out.resolve("ACCTFILE.txt"), 300);
        String id = "00000000050";
        String b50 = before.stream().filter(r -> r.startsWith(id)).findFirst().orElseThrow();
        String a50 = after.stream().filter(r -> r.startsWith(id)).findFirst().orElseThrow();
        assertEquals(b50, a50);
        assertEquals("00000019458G", Records.slice(a50, 13, 12));
        String tx = Records.read(out.resolve("TRANSACT.txt"), 350).stream()
                .filter(r -> Records.slice(r, 33, 100).contains("a/c " + id)).findFirst().orElseThrow();
        assertEquals("0000000187G", Records.slice(tx, 133, 11));
        // control: account 1 (not last) is rewritten
        assertNotEquals(before.get(0), after.get(0));
    }

    /** D6: running twice restarts the TRAN-ID suffix at 000001 (duplicate ids). */
    @Test
    void d6_suffixRestartsEveryRun(@TempDir Path t) throws Exception {
        Fx f = Fx.twoAccounts("0000001288A", "00150{").run(t);
        assertEquals("2022071800000001", Records.slice(f.transact.get(0), 1, 16));
    }

    /** D3: blank ACCT-GROUP-ID forces the DEFAULT fallback with two warning lines. */
    @Test
    void d3_blankGroupFallsBackToDefault(@TempDir Path t) throws Exception {
        Fx f = Fx.twoAccounts("0000001288A", "00150{").run(t);
        assertEquals(2, f.sysout.split("DISCLOSURE GROUP RECORD MISSING").length - 1);
        assertEquals(2, f.sysout.split("TRY WITH DEFAULT GROUP CODE").length - 1);
    }
}
