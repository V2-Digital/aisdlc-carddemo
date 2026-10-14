package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @TempDir Path tmp;

    private String amountFor(String bal, String rate) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        String a = "00000000001";
        Fixtures.lines(in.resolve("TCATBALF.txt"), List.of(Fixtures.tcat(a, "01", "0001", bal)));
        Fixtures.lines(in.resolve("ACCTFILE.txt"), List.of(Fixtures.acct(a, "00000000000{", "00000000000{", "00000000000{", "")));
        Fixtures.lines(in.resolve("XREFFILE.txt"), List.of(Fixtures.xref("1111222233334444", a)));
        Fixtures.lines(in.resolve("DISCGRP.txt"), List.of(Fixtures.disc("DEFAULT", "01", "0001", rate)));
        new InterestCalc("2022071800", Fixtures.NOW).run(in, tmp.resolve("out"));
        return Fixtures.read(tmp.resolve("out/TRANSACT.txt")).get(0).substring(132, 143);
    }

    @Test void truncatesAcct2() throws IOException {
        // 2339.97 * 15.00 / 1200 = 29.249625 -> 29.24
        assertEquals("0000000292D", amountFor("0000023399G", "00150{"));
    }

    @Test void negativeBalanceTruncatesTowardZero() throws IOException {
        // -2339.97 -> -29.24 ('M' would be -4 : 0000000292M)
        assertEquals("0000000292M", amountFor("0000023399P", "00150{"));
    }
}
