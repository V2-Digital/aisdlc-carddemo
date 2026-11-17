package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path tmp;

    private static byte[] b(Path p) throws IOException { return Files.readAllBytes(p); }

    private void check(Path in, String scenario, int records) throws IOException {
        Path gold = Fx.ROOT.resolve("legacy/golden/" + scenario);
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(Integer.parseInt(Files.readString(gold.resolve("RC.txt")).trim()), r.rc());
        assertArrayEquals(b(gold.resolve("TRANSACT.txt")), b(r.out().resolve("TRANSACT.txt")));
        assertArrayEquals(b(gold.resolve("ACCTFILE.txt")), b(r.out().resolve("ACCTFILE.txt")));
        assertEquals(records, r.records("TRANSACT.txt").size());
        assertEquals(records, r.records("ACCTFILE.txt").size());
        String sys = String.join("\n", r.sysout()) + "\n";
        assertEquals(Files.readString(gold.resolve("SYSOUT.txt"), StandardCharsets.ISO_8859_1), sys);
    }

    /** Stock sample data, as run.sh stages it: strip CR; Fx/IntCalc pad short lines. */
    @Test
    void stock() throws IOException {
        Path a = Fx.ROOT.resolve("app/data/ASCII");
        Path in = Files.createDirectories(tmp.resolve("in"));
        // names per JCL DD -> sample file
        List<String[]> m = List.of(new String[]{"TCATBALF", "tcatbal.txt"}, new String[]{"XREFFILE", "cardxref.txt"},
                new String[]{"DISCGRP", "discgrp.txt"}, new String[]{"ACCTFILE", "acctdata.txt"});
        for (String[] x : m) Files.copy(a.resolve(x[1]), in.resolve(x[0] + ".txt"));
        check(in, "CBACT04C", 50);
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path a = Fx.ROOT.resolve("app/data/ASCII");
        Path g = Fx.ROOT.resolve("legacy/golden/CBTRN02C");
        Path in = Files.createDirectories(tmp.resolve("in"));
        Files.copy(g.resolve("TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(g.resolve("ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(a.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(a.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        check(in, "CBACT04C-after-CBTRN02C", 50);
    }
}
