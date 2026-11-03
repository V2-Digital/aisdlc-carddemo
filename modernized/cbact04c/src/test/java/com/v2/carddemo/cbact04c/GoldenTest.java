package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private void check(String scenario, Path in, Path out) throws Exception {
        Fx.Result r = Fx.run(in, out, Fx.NOW);
        Path g = Fx.GOLDEN.resolve(scenario);
        assertEquals(Fx.read(g.resolve("TRANSACT.txt")), Fx.read(out.resolve("TRANSACT.txt")), "TRANSACT");
        assertEquals(Fx.read(g.resolve("ACCTFILE.txt")), Fx.read(out.resolve("ACCTFILE.txt")), "ACCTFILE");
        assertEquals(50, Fx.lines(out.resolve("TRANSACT.txt")).size());
        assertEquals(50, Fx.lines(out.resolve("ACCTFILE.txt")).size());
        List<String> expected = Fx.lines(g.resolve("SYSOUT.txt"));
        assertEquals(expected, r.sysout(), "SYSOUT");
        assertEquals(Integer.parseInt(Fx.read(g.resolve("RC.txt")).trim()), r.rc());
        assertEquals(0, r.rc());
    }

    @Test
    void stock(@TempDir Path t) throws Exception {
        Path in = Fx.stage(t.resolve("in"), Fx.DATA.resolve("tcatbal.txt"), Fx.DATA.resolve("acctdata.txt"));
        check("CBACT04C", in, t.resolve("out"));
    }

    @Test
    void afterCbtrn02c(@TempDir Path t) throws Exception {
        Path c = Fx.GOLDEN.resolve("CBTRN02C");
        Path in = Fx.stage(t.resolve("in"), c.resolve("TCATBALF.txt"), c.resolve("ACCTFILE.txt"));
        check("CBACT04C-after-CBTRN02C", in, t.resolve("out"));
    }
}
