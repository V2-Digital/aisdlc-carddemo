package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @TempDir Path tmp;

    private InterestCalc.Result run(List<String> tcat, List<String> xref, List<String> disc, List<String> acct)
            throws IOException {
        Fx.inputs(tmp, tcat, xref, disc, acct);
        return InterestCalc.run("2022071800", tmp, Fx.CLOCK);
    }

    private static void assertTail(InterestCalc.Result r, String... tail) {
        assertEquals(16, r.rc());
        assertTrue(r.transact().isEmpty() && r.accounts().isEmpty(), "no outputs on abend");
        List<String> s = r.sysout();
        assertEquals(List.of(tail), s.subList(s.size() - tail.length, s.size()));
    }

    private static final List<String> TC = List.of(Fx.tcat(Fx.A1, "01", "0001", "100"));
    private static final List<String> DISC = List.of(Fx.disc("DEFAULT", "01", "0001", "10"));

    @Test void accountNotFound() throws IOException { // P1
        assertTail(run(TC, List.of(Fx.xref(Fx.C1, Fx.A1)), DISC, List.of(Fx.acct(Fx.A2, "0", ""))),
                "ACCOUNT NOT FOUND: " + Fx.A1, "ERROR READING ACCOUNT FILE", "FILE STATUS IS: NNNN0023",
                "ABENDING PROGRAM");
    }

    @Test void xrefNotFound() throws IOException { // P4
        assertTail(run(TC, List.of(Fx.xref(Fx.C2, Fx.A2)), DISC, List.of(Fx.acct(Fx.A1, "0", ""))),
                "ACCOUNT NOT FOUND: " + Fx.A1, "ERROR READING XREF FILE", "FILE STATUS IS: NNNN0023",
                "ABENDING PROGRAM");
    }

    @Test void defaultRateMissing() throws IOException { // P5
        assertTail(run(TC, List.of(Fx.xref(Fx.C1, Fx.A1)), List.of(Fx.disc("DEFAULT", "03", "0001", "1")),
                List.of(Fx.acct(Fx.A1, "0", ""))),
                "DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM");
    }

    @Test void missingFile() throws IOException {
        Fx.inputs(tmp, TC, List.of(), DISC, List.of());
        java.nio.file.Files.delete(tmp.resolve("ACCTFILE.txt"));
        assertTail(InterestCalc.run("2022071800", tmp, Fx.CLOCK), "ERROR OPENING ACCOUNT MASTER FILE",
                "FILE STATUS IS: NNNN0035", "ABENDING PROGRAM");
    }

    @Test void invalidInputAbends() throws IOException { // D2
        InterestCalc.Result r = run(List.of(Fx.tcat(Fx.A1, "01", "0001", "100"), Fx.tcat(Fx.A1, "01", "0001", "100")),
                List.of(Fx.xref(Fx.C1, Fx.A1)), DISC, List.of(Fx.acct(Fx.A1, "0", "")));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("FILE STATUS IS: NNNN0090"));
        r = run(List.of("x".repeat(60)), List.of(), DISC, List.of());
        assertEquals(16, r.rc());
    }
}
