package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    @TempDir Path tmp;

    private void assertIdentical(Support.Result r, String scenario, int expectedTxns) throws IOException {
        Path g = Support.GOLDEN.resolve(scenario);
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt", "SYSOUT.txt", "RC.txt"}) {
            assertEquals(-1L, Files.mismatch(r.dir().resolve(f), g.resolve(f)), f + " differs from golden/" + scenario);
        }
        assertEquals(expectedTxns, Support.lines(r.transact()).size());
        assertEquals(0, r.rc());
    }

    @Test
    void stockData() throws IOException {
        assertIdentical(Support.stock(tmp), "CBACT04C", 50);
    }

    @Test
    void afterCbtrn02c() throws IOException {
        assertIdentical(Support.afterPosting(tmp), "CBACT04C-after-CBTRN02C", 50);
    }
}
