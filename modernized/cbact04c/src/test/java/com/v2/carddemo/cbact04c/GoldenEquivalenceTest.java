package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenEquivalenceTest {
    static final Path ROOT = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().resolve("../..").normalize();

    static List<String> lines(Path p) throws IOException {
        assertTrue(Files.isRegularFile(p), "missing " + p);
        List<String> l = new ArrayList<>();
        for (String s : Files.readAllLines(p, StandardCharsets.ISO_8859_1)) l.add(s.endsWith("\r") ? s.substring(0, s.length() - 1) : s);
        return l;
    }

    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        l.forEach(s -> sb.append(s).append('\n'));
        return sb.toString();
    }

    static void check(Path tcat, Path xref, Path acct, Path disc, String scenario, int expTx) throws IOException {
        var r = Cbact04c.run("2022071800", new Cbact04c.Inputs(lines(tcat), lines(xref), lines(acct), lines(disc)),
                () -> "2022071800000000");
        Path g = ROOT.resolve("legacy/golden").resolve(scenario);
        assertEquals(expTx, r.transact().size());
        for (var e : new String[][] {{"TRANSACT", join(r.transact())}, {"ACCTFILE", join(r.acctfile())},
                {"SYSOUT", join(r.sysout())}, {"RC", r.rc() + "\n"}}) {
            byte[] exp = Files.readAllBytes(g.resolve(e[0] + ".txt"));
            assertArrayEquals(exp, e[1].getBytes(StandardCharsets.ISO_8859_1), scenario + "/" + e[0]);
        }
    }

    @Test
    void stockScenario() throws IOException {
        Path d = ROOT.resolve("app/data/ASCII");
        check(d.resolve("tcatbal.txt"), d.resolve("cardxref.txt"), d.resolve("acctdata.txt"), d.resolve("discgrp.txt"),
                "CBACT04C", 50);
    }

    @Test
    void afterCbtrn02cScenario() throws IOException {
        Path d = ROOT.resolve("app/data/ASCII");
        Path g = ROOT.resolve("legacy/golden/CBTRN02C");
        check(g.resolve("TCATBALF.txt"), d.resolve("cardxref.txt"), g.resolve("ACCTFILE.txt"), d.resolve("discgrp.txt"),
                "CBACT04C-after-CBTRN02C", 50);
    }
}
