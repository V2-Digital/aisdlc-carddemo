package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private static final Path ASCII = Fx.ROOT.resolve("app/data/ASCII");
    private static final Path GOLD = Fx.ROOT.resolve("legacy/golden");

    private void scenario(Path in, String golden, int stdoutLines, Path tmp) throws IOException {
        Path out = tmp.resolve("out");
        Object[] r = Fx.cli("--parm", "2022071800", "--clock", "2022071800000000", "--in", in.toString(),
                "--out", out.toString());
        assertEquals(0, r[0]);
        for (String f : List.of("TRANSACT.txt", "ACCTFILE.txt")) {
            assertArrayEquals(Fx.bytes(GOLD.resolve(golden).resolve(f)), Fx.bytes(out.resolve(f)), f);
        }
        // bonus: DISPLAY output equals SYSOUT.txt
        String sysout = Files.readString(GOLD.resolve(golden).resolve("SYSOUT.txt"), StandardCharsets.ISO_8859_1);
        assertEquals(sysout, (String) r[1]);
        assertEquals(stdoutLines, sysout.split("\n", -1).length - 1);
    }

    @Test
    void stock(@TempDir Path tmp) throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Files.copy(ASCII.resolve("tcatbal.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("acctdata.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        scenario(in, "CBACT04C", 152, tmp);
        assertEquals(50, Fx.lines(tmp.resolve("out/TRANSACT.txt"), 350).size());
        assertEquals(50, Fx.lines(tmp.resolve("out/ACCTFILE.txt"), 300).size());
    }

    @Test
    void afterCbtrn02c(@TempDir Path tmp) throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Files.copy(GOLD.resolve("CBTRN02C/TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(GOLD.resolve("CBTRN02C/ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        scenario(in, "CBACT04C-after-CBTRN02C", 302, tmp);
        assertEquals(50, Fx.lines(tmp.resolve("out/TRANSACT.txt"), 350).size());
        // 49 of 50 accounts change versus the input (D1: account 50 does not)
        List<String> a = Fx.lines(in.resolve("ACCTFILE.txt"), 300);
        List<String> b = Fx.lines(tmp.resolve("out/ACCTFILE.txt"), 300);
        long diff = 0;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                diff++;
            }
        }
        assertEquals(49, diff);
    }
}
