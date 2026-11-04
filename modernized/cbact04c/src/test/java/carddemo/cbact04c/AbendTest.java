package carddemo.cbact04c;

import static carddemo.cbact04c.Base.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC6: DEFAULT fallback and the abend paths (exit 16). */
class AbendTest {
    @TempDir Path tmp;

    private final List<String> tcat = List.of(Fx.bal(A1, "010001", amt("100.00")));
    private final List<String> xref = List.of(Fx.xref("4111111111111111", A1));
    private final List<String> acct = List.of(acct(A1, "10.00"));
    private final List<String> defaultDisc = List.of(Fx.disc("DEFAULT", "010001", Zoned.encode(new java.math.BigDecimal("12.00"), 4)));

    @Test
    void missingGroupFallsBackToDefault() throws IOException {
        Fx.Result r = run(tmp, tcat, xref, acct, defaultDisc);
        assertEquals(0, r.rc());
        assertTrue(r.stdout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals(1, Fx.lines(r.out().resolve("TRANSACT.txt")).size());
    }

    @Test
    void exactGroupMatchDoesNotFallBack() throws IOException {
        List<String> a = List.of(Fx.acct(A1, bal("10.00"), "00000000500{", "00000000100}", "GRP"));
        Fx.Result r = run(tmp, tcat, xref, a, List.of(Fx.disc("GRP", "010001", Zoned.encode(new java.math.BigDecimal("12.00"), 4))));
        assertEquals(0, r.rc());
        assertFalse(r.stdout().contains("MISSING"));
    }

    @Test
    void missingDefault() throws IOException {
        Fx.Result r = run(tmp, tcat, xref, acct, List.of(Fx.disc("DEFAULT", "020001", "0100{")));
        assertEquals(16, r.rc());
        assertTrue(r.stdout().endsWith("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"
                + "ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), r.stdout());
        assertEquals("CEE3ABD: ABEND U0999\n", r.stderr());
    }

    @Test
    void missingAccount() throws IOException {
        Fx.Result r = run(tmp, tcat, xref, List.of(acct(A2, "10.00")), defaultDisc);
        assertEquals(16, r.rc());
        assertTrue(r.stdout().endsWith("ACCOUNT NOT FOUND: " + A1 + "\nERROR READING ACCOUNT FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), r.stdout());
    }

    @Test
    void missingXref() throws IOException {
        Fx.Result r = run(tmp, tcat, List.of(Fx.xref("4111111111111111", A2)), acct, defaultDisc);
        assertEquals(16, r.rc());
        assertTrue(r.stdout().endsWith("ACCOUNT NOT FOUND: " + A1 + "\nERROR READING XREF FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), r.stdout());
    }

    @Test
    void absentInputFile() throws IOException {
        Path in = Fx.stage(tmp.resolve("in"), tcat, xref, acct, defaultDisc);
        java.nio.file.Files.delete(in.resolve("DISCGRP.txt"));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(16, r.rc());
        assertTrue(r.stdout().contains("ERROR OPENING DALY REJECTS FILE\nFILE STATUS IS: NNNN0035\n"), r.stdout()); // D5
    }
}
