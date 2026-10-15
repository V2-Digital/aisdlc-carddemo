package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Expected text was cross-checked against the COBOL by legacy/tests/cbact04c-edge.sh. */
class AbendTest {
    private static String tail(String sysout, int n) {
        var l = sysout.lines().toList();
        return l.subList(l.size() - n, l.size()).stream().collect(Collectors.joining("\n"));
    }

    @Test
    void E1_accountMissingFromAccountFile(@TempDir Path tmp) throws Exception {
        var r = Fx.run(tmp, Map.of(), Map.of("TCATBALF", Fx.tcat("99999999999", "01", "0001", "0000000010{")));
        assertEquals(16, r.rc());
        assertEquals("ACCOUNT NOT FOUND: 99999999999\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM",
                tail(r.sysout(), 4));
        assertTrue(r.stderr().contains("CEE3ABD: ABEND U0999"));
    }

    @Test
    void E2_accountMissingFromXref(@TempDir Path tmp) throws Exception {
        String xref = Files.readAllLines(Fx.stock("XREFFILE")).stream()
                .filter(l -> !l.substring(25, 36).equals("00000000001")).collect(Collectors.joining("\n", "", "\n"));
        var r = Fx.run(tmp, Map.of(), Map.of("TCATBALF", Fx.tcat("00000000001", "01", "0001", "0000000010{"), "XREFFILE", xref));
        assertEquals(16, r.rc());
        assertEquals("ACCOUNT NOT FOUND: 00000000001\nERROR READING XREF FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM",
                tail(r.sysout(), 4));
    }

    @Test
    void E3_noDefaultDisclosureGroup(@TempDir Path tmp) throws Exception {
        String disc = Files.readAllLines(Fx.stock("DISCGRP")).stream()
                .filter(l -> !l.startsWith("DEFAULT")).collect(Collectors.joining("\n", "", "\n"));
        var r = Fx.run(tmp, Map.of(), Map.of("TCATBALF", Fx.tcat("00000000001", "01", "0001", "0000000010{"), "DISCGRP", disc));
        assertEquals(16, r.rc());
        assertEquals("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\nERROR READING DEFAULT DISCLOSURE GROUP\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM", tail(r.sysout(), 5));
    }
}
