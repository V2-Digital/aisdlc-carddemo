package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path tmp;
    final Path root = Fx.root();

    private void check(String scenario, Path tcat, Path acct, int trans, int accts, int sysoutLines) throws IOException {
        Path ascii = root.resolve("app/data/ASCII"), g = root.resolve("legacy/golden/" + scenario);
        var out = new java.io.ByteArrayOutputStream();
        int rc = Main.run(new String[] {"--PARM=2022071800", "--CURRENT-DATE=" + Fx.CLOCK, "--TCATBALF=" + tcat,
                "--XREFFILE=" + ascii.resolve("cardxref.txt"), "--DISCGRP=" + ascii.resolve("discgrp.txt"),
                "--ACCTFILE=" + acct, "--ACCTFILE-OUT=" + tmp.resolve("A"), "--TRANSACT=" + tmp.resolve("T")},
                new java.io.PrintStream(out, true, StandardCharsets.ISO_8859_1), System.err);
        assertEquals(Integer.parseInt(Files.readString(g.resolve("RC.txt")).trim()), rc);
        assertArrayEquals(Files.readAllBytes(g.resolve("TRANSACT.txt")), Files.readAllBytes(tmp.resolve("T")));
        assertArrayEquals(Files.readAllBytes(g.resolve("ACCTFILE.txt")), Files.readAllBytes(tmp.resolve("A")));
        assertArrayEquals(Files.readAllBytes(g.resolve("SYSOUT.txt")), out.toByteArray());
        assertEquals(trans, Files.readAllLines(tmp.resolve("T")).size());
        assertEquals(accts, Files.readAllLines(tmp.resolve("A")).size());
        assertEquals(sysoutLines, Files.readAllLines(g.resolve("SYSOUT.txt")).size());
    }

    @Test void stock() throws IOException {
        Path a = root.resolve("app/data/ASCII");
        check("CBACT04C", a.resolve("tcatbal.txt"), a.resolve("acctdata.txt"), 50, 50, 152);
    }

    @Test void afterPosting() throws IOException {
        Path c = root.resolve("legacy/golden/CBTRN02C");
        check("CBACT04C-after-CBTRN02C", c.resolve("TCATBALF.txt"), c.resolve("ACCTFILE.txt"), 50, 50, 302);
    }

    /** F1: last account (50) is charged 18.77 but its ACCTFILE record is unchanged from CBTRN02C output. */
    @Test void f1_lastAccountNotWrittenBack_golden() throws IOException {
        Path c = root.resolve("legacy/golden/CBTRN02C");
        var before = Files.readAllLines(c.resolve("ACCTFILE.txt"), StandardCharsets.ISO_8859_1);
        var after = Files.readAllLines(root.resolve("legacy/golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt"), StandardCharsets.ISO_8859_1);
        var tr = Files.readAllLines(root.resolve("legacy/golden/CBACT04C-after-CBTRN02C/TRANSACT.txt"), StandardCharsets.ISO_8859_1);
        assertEquals(before.get(49), after.get(49));
        assertTrue(tr.get(49).startsWith("2022071800000050"));
        assertEquals("0000000187G", Fx.amt(tr.get(49)).replace("0000000187", "0000000187")); // 18.77 -> 187 + 'G'(7)
        int changed = 0;
        for (int i = 0; i < 50; i++) if (!before.get(i).equals(after.get(i))) changed++;
        assertEquals(49, changed);
    }
}
