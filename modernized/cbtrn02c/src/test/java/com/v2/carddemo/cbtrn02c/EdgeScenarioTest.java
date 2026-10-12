package com.v2.carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Inputs/expected outputs in src/test/resources/edge were captured from the real COBOL via legacy/harness/run.sh. */
class EdgeScenarioTest {
    private static final Path EDGE = Path.of("src/test/resources/edge");

    private void check(String c, boolean outputs) throws Exception {
        Path in = EDGE.resolve(c).resolve("in"), exp = EDGE.resolve(c).resolve("expected");
        Path d = Files.createTempDirectory(c);
        for (String dd : new String[] {"DALYTRAN", "XREFFILE", "ACCTFILE", "TCATBALF"})
            Files.copy(in.resolve(dd + ".txt"), d.resolve(dd));
        Fx.Result r = Fx.run(d);
        assertEquals(Files.readString(exp.resolve("RC.txt")).trim(), String.valueOf(r.rc()), c + " RC");
        assertEquals(Files.readString(exp.resolve("SYSOUT.txt")), r.out(), c + " SYSOUT");
        if (outputs)
            for (String dd : List.of("TRANFILE", "DALYREJS", "ACCTFILE", "TCATBALF"))
                assertArrayEquals(Files.readAllBytes(exp.resolve(dd + ".txt")), Files.readAllBytes(d.resolve(dd)), c + " " + dd);
    }

    @Test void tcatbalFillerInheritance_D9() throws Exception { check("tcatbal-filler", true); }
    @Test void zeroSignAndNegativeZeroAmount() throws Exception { check("zero-sign", true); }
    @Test void tempBalOverflowLetsOverlimitPass_D7() throws Exception { check("temp-overflow", true); }
    @Test void balanceOverflowTruncates_D7() throws Exception { check("bal-overflow", true); }
    @Test void duplicateTranIdAbends_D4() throws Exception { check("dup-tranid", false); }
}
