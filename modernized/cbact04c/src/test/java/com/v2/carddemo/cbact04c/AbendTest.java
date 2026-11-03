package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Message text verified against real COBOL by legacy/tests/cbact04c_abend.sh. */
class AbendTest {
    private static final String TC = Fx.tcat("00000000001", "01", "0001", "0000000000{");
    private static final String DEF = Fx.disc("DEFAULT", "01", "0001", "00150{");

    private void expect(Fx.Result r, String... tail) {
        assertEquals(16, r.rc());
        assertTrue(r.err().contains("CEE3ABD: ABEND U0999"));
        List<String> s = r.sysout();
        assertEquals(List.of(tail), s.subList(s.size() - tail.length, s.size()));
    }

    @Test
    void missingAccount(@TempDir Path t) throws Exception {
        Path in = Fx.synth(t.resolve("in"), List.of(TC), List.of(DEF), true, false);
        expect(Fx.run(in, t.resolve("o"), Fx.NOW), "ACCOUNT NOT FOUND: 00000000001", "ERROR READING ACCOUNT FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM");
    }

    @Test
    void missingXref(@TempDir Path t) throws Exception {
        Path in = Fx.synth(t.resolve("in"), List.of(TC), List.of(DEF), false, true);
        expect(Fx.run(in, t.resolve("o"), Fx.NOW), "ACCOUNT NOT FOUND: 00000000001", "ERROR READING XREF FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM");
    }

    @Test
    void missingDefaultGroup(@TempDir Path t) throws Exception {
        Path in = Fx.synth(t.resolve("in"), List.of(TC), List.of(), true, true);
        expect(Fx.run(in, t.resolve("o"), Fx.NOW), "DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM");
    }
}
