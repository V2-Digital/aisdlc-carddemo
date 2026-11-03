package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Expected tails come from the real COBOL run by legacy/tests/cbact04c-abend.sh (same files). */
class AbendTest {
    private static final Path FIX = BASE.resolve("../../legacy/tests/cbact04c-abend").normalize();

    private String expected(String name) throws IOException {
        return text(FIX.resolve(name));
    }

    private Path nonKey(Path d, String name, String... r) throws IOException {
        return write(d, name, r);
    }

    @Test
    void missingAccount(@TempDir Path d) throws IOException {
        Result r = run(FIX.resolve("a/TCATBALF.txt"), ASCII.resolve("cardxref.txt"), ASCII.resolve("discgrp.txt"),
                ASCII.resolve("acctdata.txt"), d.resolve("o"));
        assertEquals(16, r.rc());
        assertTrue(r.stdout().endsWith(expected("account.expected")), r.stdout());
    }

    @Test
    void missingXref(@TempDir Path d) throws IOException {
        Path xref = d.resolve("x");
        Files.write(xref, Files.readAllLines(ASCII.resolve("cardxref.txt")).stream()
                .filter(l -> !(l.length() >= 36 && l.substring(25, 36).equals("00000000001")))
                .map(l -> l + "\n").reduce("", String::concat).getBytes());
        Result r = run(FIX.resolve("x/TCATBALF.txt"), xref, ASCII.resolve("discgrp.txt"),
                ASCII.resolve("acctdata.txt"), d.resolve("o"));
        assertEquals(16, r.rc());
        assertTrue(r.stdout().endsWith(expected("xref.expected")), r.stdout());
    }

    @Test
    void missingDefaultGroup(@TempDir Path d) throws IOException {
        Result r = run(FIX.resolve("d/TCATBALF.txt"), ASCII.resolve("cardxref.txt"), FIX.resolve("d/DISCGRP.txt"),
                ASCII.resolve("acctdata.txt"), d.resolve("o"));
        assertEquals(16, r.rc());
        assertTrue(r.stdout().endsWith(expected("default.expected")), r.stdout());
    }

    @Test
    void abendWritesNoOutputFiles(@TempDir Path d) throws IOException {
        run(FIX.resolve("a/TCATBALF.txt"), ASCII.resolve("cardxref.txt"), ASCII.resolve("discgrp.txt"),
                ASCII.resolve("acctdata.txt"), d.resolve("o"));
        assertFalse(Files.exists(d.resolve("o/TRANSACT.txt")));
    }

    @Test
    void emptyTcatbalfIsCleanRun(@TempDir Path d) throws IOException {
        Path empty = Files.createFile(d.resolve("t"));
        Result r = run(empty, ASCII.resolve("cardxref.txt"), ASCII.resolve("discgrp.txt"),
                ASCII.resolve("acctdata.txt"), d.resolve("o"));
        assertEquals(0, r.rc());
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n", r.stdout());
        assertEquals(0, bytes(d.resolve("o/TRANSACT.txt")).length);
        assertArrayEquals(bytes(ASCII.resolve("acctdata.txt")), bytes(d.resolve("o/ACCTFILE.txt")));
    }
}
