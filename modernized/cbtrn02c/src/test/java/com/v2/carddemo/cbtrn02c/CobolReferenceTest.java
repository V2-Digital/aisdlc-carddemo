package com.v2.carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Fixtures in legacy/tests/cbtrn02c_reasons/ were run through the real COBOL (legacy/tests/cbtrn02c_reasons.sh);
 * the outputs are in src/test/resources/reasons/. The Java must match them byte for byte.
 */
class CobolReferenceTest {
    static final Path FIX = Harness.ROOT.resolve("legacy/tests/cbtrn02c_reasons");
    static final Path REF = Path.of("src/test/resources/reasons").toAbsolutePath();

    static Stream<String> cases() {
        return Stream.of("reasons", "norejects", "overflow", "overflow_acct");
    }

    @ParameterizedTest @MethodSource("cases")
    void matchesCobol(String c, @TempDir Path work) throws IOException {
        Harness h = Harness.run(work, Harness.inputsFrom(FIX.resolve(c)), Harness.FROZEN);
        assertEquals(Harness.read(REF.resolve(c + "/RC.txt")).trim(), String.valueOf(h.rc), "RC");
        assertEquals(Harness.read(REF.resolve(c + "/SYSOUT.txt")), h.stdout, "SYSOUT");
        for (String f : new String[] {"TRANFILE", "DALYREJS"}) {
            assertEquals(Harness.read(REF.resolve(c + "/" + f + ".txt")), h.out(f), c + "/" + f);
        }
        for (String f : new String[] {"ACCTFILE", "TCATBALF"}) {
            assertEquals(Harness.read(REF.resolve(c + "/" + f + ".txt")), Harness.read(work.resolve(f + ".txt")), c + "/" + f);
        }
    }

    @org.junit.jupiter.api.Test void duplicateTranIdAbendsLikeCobol(@TempDir Path work) throws IOException {
        Harness h = Harness.run(work, Harness.inputsFrom(FIX.resolve("dupid")), Harness.FROZEN);
        assertEquals(16, h.rc);
        assertEquals("16", Harness.read(REF.resolve("dupid/RC.txt")).trim());
        assertTrue(h.stdout.contains("FILE STATUS IS: NNNN0022"));
        assertTrue(h.stderr.contains("CEE3ABD: ABEND U0999"));
        assertFalse(work.resolve("TRANFILE.txt").toFile().exists(), "no outputs exported on abend");
    }
}
