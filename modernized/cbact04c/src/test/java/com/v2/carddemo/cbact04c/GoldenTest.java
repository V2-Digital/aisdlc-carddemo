package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    @TempDir Path tmp;

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        l.forEach(s -> sb.append(s).append('\n'));
        return sb.toString();
    }

    private void check(String scenario, boolean afterCbtrn) throws IOException {
        Path repo = Fx.repo();
        Path ascii = repo.resolve("app/data/ASCII");
        Path golden = repo.resolve("legacy/golden").resolve(scenario);
        Path in = Files.createDirectory(tmp.resolve("in"));
        Files.copy(ascii.resolve("tcatbal.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(ascii.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ascii.resolve("acctdata.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(ascii.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        if (afterCbtrn) {
            Path pre = repo.resolve("legacy/golden/CBTRN02C");
            Files.copy(pre.resolve("ACCTFILE.txt"), in.resolve("ACCTFILE.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(pre.resolve("TCATBALF.txt"), in.resolve("TCATBALF.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        InterestCalc.Result r = InterestCalc.run("2022071800", in, Fx.CLOCK);
        assertEquals(0, r.rc());
        assertEquals(50, r.transact().size());
        assertEquals(50, r.accounts().size());
        assertEquals(Files.readString(golden.resolve("TRANSACT.txt")), join(r.transact()), "TRANSACT");
        assertEquals(Files.readString(golden.resolve("ACCTFILE.txt")), join(r.accounts()), "ACCTFILE");
        assertEquals(Files.readString(golden.resolve("SYSOUT.txt")), join(r.sysout()), "SYSOUT");
        assertEquals(Files.readString(golden.resolve("RC.txt")).trim(), String.valueOf(r.rc()), "RC");
    }

    @Test void stock() throws IOException { check("CBACT04C", false); }

    @Test void afterCbtrn02c() throws IOException { check("CBACT04C-after-CBTRN02C", true); }
}
