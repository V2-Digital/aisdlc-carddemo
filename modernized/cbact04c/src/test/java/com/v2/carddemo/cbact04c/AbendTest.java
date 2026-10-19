package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @TempDir Path tmp;

    private Path sub(String name, List<String> lines) throws IOException {
        return Support.write(tmp.resolve(name), lines);
    }

    private List<String> sample(String f, int n) throws IOException {
        return new ArrayList<>(Support.lines(Support.SAMPLE.resolve(f)).subList(0, n));
    }

    private List<String> tail(List<String> l, int n) {
        return l.subList(l.size() - n, l.size());
    }

    @Test
    void missingAccount() throws IOException {
        Support.Result r = Support.run(Support.SAMPLE.resolve("tcatbal.txt"), Support.SAMPLE.resolve("cardxref.txt"),
                Support.SAMPLE.resolve("discgrp.txt"), sub("acct.txt", sample("acctdata.txt", 0)), tmp.resolve("o"));
        assertEquals(16, r.rc());
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000001", "ERROR READING ACCOUNT FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), tail(r.sysout(), 4));
    }

    @Test
    void missingXref() throws IOException {
        Support.Result r = Support.run(Support.SAMPLE.resolve("tcatbal.txt"), sub("x.txt", List.of()),
                Support.SAMPLE.resolve("discgrp.txt"), Support.SAMPLE.resolve("acctdata.txt"), tmp.resolve("o"));
        assertEquals(16, r.rc());
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000001", "ERROR READING XREF FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), tail(r.sysout(), 4));
    }

    @Test
    void missingDefaultDisclosureGroup() throws IOException {
        Support.Result r = Support.run(Support.SAMPLE.resolve("tcatbal.txt"), Support.SAMPLE.resolve("cardxref.txt"),
                sub("d.txt", List.of()), Support.SAMPLE.resolve("acctdata.txt"), tmp.resolve("o"));
        assertEquals(16, r.rc());
        assertEquals(List.of("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"),
                tail(r.sysout(), 5));
    }

    @Test
    void openErrorForDiscgrpKeepsLegacyTypoMessage() throws IOException {
        Support.Result r = Support.run(Support.SAMPLE.resolve("tcatbal.txt"), Support.SAMPLE.resolve("cardxref.txt"),
                tmp.resolve("nope.txt"), Support.SAMPLE.resolve("acctdata.txt"), tmp.resolve("o"));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR OPENING DALY REJECTS FILE"));
    }

    @Test
    void emptyTcatbalCopiesAccountsUnchanged() throws IOException {
        Support.Result r = Support.run(sub("t.txt", List.of()), Support.SAMPLE.resolve("cardxref.txt"),
                Support.SAMPLE.resolve("discgrp.txt"), Support.SAMPLE.resolve("acctdata.txt"), tmp.resolve("o"));
        assertEquals(0, r.rc());
        assertEquals(0, Support.lines(r.transact()).size());
        assertEquals(-1L, Files.mismatch(r.acct(), Support.SAMPLE.resolve("acctdata.txt")));
        assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C", "END OF EXECUTION OF PROGRAM CBACT04C"),
                r.sysout());
    }

    @Test
    void badClockSettingRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Clock.of("2022/07/18"));
    }
}
