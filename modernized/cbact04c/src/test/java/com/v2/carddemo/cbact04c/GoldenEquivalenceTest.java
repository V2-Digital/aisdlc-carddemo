package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path tmp;

    private void check(String scenario, boolean after, int sysoutLines) throws IOException {
        Path in = Fixtures.stageInputs(tmp.resolve("in"), after);
        Path out = tmp.resolve("out");
        int rc = new InterestCalc("2022071800", Fixtures.NOW).run(in, out);
        assertEquals(Integer.parseInt(Files.readString(Fixtures.golden(scenario, "RC.txt")).trim()), rc);
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt", "SYSOUT.txt"}) {
            assertArrayEquals(Files.readAllBytes(Fixtures.golden(scenario, f)),
                    Files.readAllBytes(out.resolve(f)), scenario + "/" + f);
        }
        assertEquals(50, Files.readAllLines(out.resolve("TRANSACT.txt")).size());
        assertEquals(50 * 351, Files.size(out.resolve("TRANSACT.txt")));
        assertEquals(50 * 301, Files.size(out.resolve("ACCTFILE.txt")));
        assertEquals(sysoutLines, Files.readAllLines(out.resolve("SYSOUT.txt")).size());
    }

    @Test void stockScenario() throws IOException { check("CBACT04C", false, 152); }

    @Test void afterCbtrn02cScenario() throws IOException { check("CBACT04C-after-CBTRN02C", true, 302); }
}
