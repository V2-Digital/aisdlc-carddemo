package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    private static final Path ASCII = Fx.ROOT.resolve("app/data/ASCII");
    private static final Path GOLDEN = Fx.ROOT.resolve("legacy/golden");

    private static void check(Path tmp, String in, String scenario, int records, int sysoutLines) {
        Map<String, String> m = new HashMap<>();
        m.put("TCATBALF", Fx.read(in == null ? ASCII.resolve("tcatbal.txt") : GOLDEN.resolve(in).resolve("TCATBALF.txt")));
        m.put("XREFFILE", Fx.read(ASCII.resolve("cardxref.txt")));
        m.put("DISCGRP", Fx.read(ASCII.resolve("discgrp.txt")));
        m.put("ACCTFILE", Fx.read(in == null ? ASCII.resolve("acctdata.txt") : GOLDEN.resolve(in).resolve("ACCTFILE.txt")));
        Fx.Result r = Fx.run(tmp, m);
        Path g = GOLDEN.resolve(scenario);
        assertEquals(Integer.parseInt(Fx.read(g.resolve("RC.txt")).trim()), r.rc());
        assertEquals(0, r.rc());
        assertEquals(Fx.read(g.resolve("TRANSACT.txt")), r.file("TRANSACT"), "TRANSACT");
        assertEquals(Fx.read(g.resolve("ACCTFILE.txt")), r.file("ACCTFILE"), "ACCTFILE");
        assertEquals(Fx.read(g.resolve("SYSOUT.txt")), r.sysout(), "SYSOUT");
        assertEquals(records, r.lines("TRANSACT").size());
        assertEquals(50, r.lines("ACCTFILE").size());
        assertEquals(sysoutLines, r.sysout().lines().count());
    }

    @Test
    void stock(@TempDir Path tmp) { check(tmp, null, "CBACT04C", 50, 152); }

    @Test
    void afterCbtrn02c(@TempDir Path tmp) { check(tmp, "CBTRN02C", "CBACT04C-after-CBTRN02C", 50, 302); }
}
