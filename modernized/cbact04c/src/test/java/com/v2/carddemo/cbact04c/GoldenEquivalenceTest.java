package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GoldenEquivalenceTest {
    private static final String CLOCK = "2022071800000000";

    private void check(String scenario, Path inputDir) throws IOException {
        Harness.Result r = Harness.run(inputDir, CLOCK);
        Path g = Harness.GOLDEN.resolve(scenario);
        assertEquals(0, r.rc());
        assertEquals(Harness.read(g.resolve("RC.txt")).trim(), "0");
        assertEquals(Harness.read(g.resolve("TRANSACT.txt")), r.transact(), "TRANSACT");
        assertEquals(Harness.read(g.resolve("ACCTFILE.txt")), r.acctfile(), "ACCTFILE");
        assertEquals(Harness.read(g.resolve("SYSOUT.txt")), r.sysout(), "SYSOUT");
    }

    @Test
    void stockData() throws IOException {
        check("CBACT04C", null);
    }

    @Test
    void afterPosting() throws IOException {
        Path in = Files.createTempDirectory("in");
        for (String dd : new String[] {"ACCTFILE", "TCATBALF"}) {
            Files.copy(Harness.GOLDEN.resolve("CBTRN02C/" + dd + ".txt"), in.resolve(dd + ".txt"));
        }
        check("CBACT04C-after-CBTRN02C", in);
    }
}
