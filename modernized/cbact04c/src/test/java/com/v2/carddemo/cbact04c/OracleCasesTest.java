package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Cases whose expected outputs were produced by the COBOL (legacy/tests/cbact04c-oracle.sh). */
class OracleCasesTest {
    private static List<Path> cases() throws IOException {
        try (Stream<Path> s = Files.list(Harness.CASES)) {
            return s.filter(Files::isDirectory).sorted().toList();
        }
    }

    @Test
    void allOracleCasesMatchCobol() throws IOException {
        List<Path> cases = cases();
        assertTrue(cases.size() >= 7, "oracle cases missing under " + Harness.CASES);
        for (Path c : cases) {
            String name = c.getFileName().toString();
            Path exp = c.resolve("expected");
            Harness.Result r = Harness.run(c.resolve("input"), "2022071800000000");
            assertEquals(Integer.parseInt(Harness.read(exp.resolve("RC.txt")).trim()), r.rc(), name + " RC");
            assertEquals(Harness.read(exp.resolve("SYSOUT.txt")), r.sysout(), name + " SYSOUT");
            if (r.rc() == 0) { // abends export no datasets from the harness
                assertEquals(Harness.read(exp.resolve("TRANSACT.txt")), r.transact(), name + " TRANSACT");
                assertEquals(Harness.read(exp.resolve("ACCTFILE.txt")), r.acctfile(), name + " ACCTFILE");
            }
        }
    }
}
