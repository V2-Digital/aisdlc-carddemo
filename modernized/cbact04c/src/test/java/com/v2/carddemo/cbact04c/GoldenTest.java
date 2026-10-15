package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private void assertGolden(Fx.Result r, String scenario) throws IOException {
        Path g = Fx.GOLDEN.resolve(scenario);
        assertEquals(0, r.rc());
        assertEquals(Fx.bytes(g.resolve("RC.txt")).trim(), "0");
        assertEquals(Fx.bytes(g.resolve("SYSOUT.txt")), r.sysout(), "SYSOUT");
        assertEquals(Fx.bytes(g.resolve("TRANSACT.txt")), Fx.bytes(r.out().resolve("TRANSACT.txt")), "TRANSACT");
        assertEquals(Fx.bytes(g.resolve("ACCTFILE.txt")), Fx.bytes(r.out().resolve("ACCTFILE.txt")), "ACCTFILE");
        assertEquals(50, r.lines("TRANSACT.txt").size());
        assertEquals(50, r.lines("ACCTFILE.txt").size());
    }

    @Test
    void stock(@TempDir Path tmp) throws IOException {
        assertGolden(Fx.run(tmp, Map.of(), Map.of()), "CBACT04C");
    }

    @Test
    void afterCbtrn02c(@TempDir Path tmp) throws IOException {
        Path g = Fx.GOLDEN.resolve("CBTRN02C");
        var r = Fx.run(tmp, Map.of("ACCTFILE", g.resolve("ACCTFILE.txt"), "TCATBALF", g.resolve("TCATBALF.txt")), Map.of());
        assertGolden(r, "CBACT04C-after-CBTRN02C");
    }
}
