package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @TempDir Path tmp;
    static final String A1 = "00000000001";
    static final String TC = Fx.tcat(A1, "01", "0001", "100.00");

    private Fx.Result run(List<String> xref, List<String> disc, List<String> acct) throws IOException {
        Path in = Fx.dir(tmp, "in", List.of(TC), xref, disc, acct);
        return Fx.run(in, tmp.resolve("out"));
    }

    private static final List<String> OK_X = List.of(Fx.xref("4000000000010000", A1));
    private static final List<String> OK_A = List.of(Fx.acct(A1, "1.00", "0", "0", ""));

    @Test
    void defaultFallbackPrintsMissingLines() throws IOException {
        Fx.Result r = run(OK_X, List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")), OK_A);
        assertEquals(0, r.rc());
        assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C", TC, "DISCLOSURE GROUP RECORD MISSING",
                "TRY WITH DEFAULT GROUP CODE", "END OF EXECUTION OF PROGRAM CBACT04C"), r.sysout());
    }

    @Test
    void missingDefault() throws IOException {
        Fx.Result r = run(OK_X, List.of(Fx.disc("DEFAULT", "02", "0001", "12.00")), OK_A);
        assertEquals(16, r.rc());
        assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C", TC, "DISCLOSURE GROUP RECORD MISSING",
                "TRY WITH DEFAULT GROUP CODE", "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023",
                "ABENDING PROGRAM"), r.sysout());
        assertFalse(Files.exists(r.out().resolve("TRANSACT.txt")));
        assertFalse(Files.exists(r.out().resolve("ACCTFILE.txt")));
    }

    @Test
    void missingAccount() throws IOException {
        Fx.Result r = run(OK_X, List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")),
                List.of(Fx.acct("00000000002", "1.00", "0", "0", "")));
        assertEquals(16, r.rc());
        assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C", TC, "ACCOUNT NOT FOUND: " + A1,
                "ERROR READING ACCOUNT FILE", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), r.sysout());
        assertFalse(Files.exists(r.out().resolve("TRANSACT.txt")));
    }

    @Test
    void missingXref() throws IOException {
        Fx.Result r = run(List.of(Fx.xref("4000000000020000", "00000000002")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")), OK_A);
        assertEquals(16, r.rc());
        assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C", TC, "ACCOUNT NOT FOUND: " + A1,
                "ERROR READING XREF FILE", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), r.sysout());
    }
}
