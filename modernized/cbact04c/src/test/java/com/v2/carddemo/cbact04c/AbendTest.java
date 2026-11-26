package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Fixture.*;
import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AbendTest {
    @Test
    void missingDefaultGroupAbends() throws Exception {
        Result r = run("2022071800", inputs("0000001164H", "00000019458G", "", disc("OTHER", "01", "0001", "001500")));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
        assertFalse(r.sysout().contains("END OF EXECUTION"));
    }

    @Test
    void missingAccountAbends() throws Exception {
        Path in = inputs("0000001164H", "00000019458G", "", disc("DEFAULT", "01", "0001", "001500"));
        put(in, "ACCTFILE", acct(A2, ZERO12, ZERO12, ZERO12, ""));
        Result r = run("2022071800", in);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ACCOUNT NOT FOUND: 00000000001\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
    }

    @Test
    void missingXrefAbends() throws Exception {
        Path in = inputs("0000001164H", "00000019458G", "", disc("DEFAULT", "01", "0001", "001500"));
        put(in, "XREFFILE", xref("0500024453765741", A2));
        Result r = run("2022071800", in);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR READING XREF FILE"));
    }

    @Test
    void missingInputFileAbends() throws Exception {
        Path in = inputs("0000001164H", "00000019458G", "", "");
        Files.delete(in.resolve("DISCGRP.txt"));
        Result r = run("2022071800", in);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR OPENING DALY REJECTS FILE")); // F6 wrong message preserved
    }
}
