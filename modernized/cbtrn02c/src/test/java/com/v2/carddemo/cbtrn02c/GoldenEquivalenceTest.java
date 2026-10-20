package com.v2.carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC 2-5: stock inputs, frozen clock; every output byte-identical to legacy/golden/CBTRN02C. */
class GoldenEquivalenceTest {
    static final Path GOLDEN = Harness.ROOT.resolve("legacy/golden/CBTRN02C");

    @Test void stockDataIsByteIdenticalToGolden(@TempDir Path work) throws IOException {
        Harness h = Harness.run(work, Harness.stock(), Harness.FROZEN);
        assertEquals(4, h.rc);
        assertEquals(Harness.read(GOLDEN.resolve("SYSOUT.txt")), h.stdout, "SYSOUT");
        assertEquals("4\n", Harness.read(GOLDEN.resolve("RC.txt")));
        assertEquals("", h.stderr);
        for (String f : new String[] {"TRANFILE", "DALYREJS"}) {
            assertEquals(Harness.read(GOLDEN.resolve(f + ".txt")), h.out(f), f);
        }
        // I-O files are updated in place in the harness; the Java writes them to the DD path.
        for (String f : new String[] {"ACCTFILE", "TCATBALF"}) {
            assertEquals(Harness.read(GOLDEN.resolve(f + ".txt")), Harness.read(work.resolve(f + ".txt")), f);
        }
    }

    @Test void recordCounts(@TempDir Path work) throws IOException {
        Harness h = Harness.run(work, Harness.stock(), Harness.FROZEN);
        assertEquals(262, h.out("TRANFILE").split("\n").length);
        assertEquals(38, h.out("DALYREJS").split("\n").length);
        assertEquals(50, Harness.read(work.resolve("ACCTFILE.txt")).split("\n").length);
        assertEquals(100, Harness.read(work.resolve("TCATBALF.txt")).split("\n").length);
        assertTrue(h.stdout.contains("TRANSACTIONS PROCESSED :000000300"));
        assertTrue(h.stdout.contains("TRANSACTIONS REJECTED  :000000038"));
    }
}
