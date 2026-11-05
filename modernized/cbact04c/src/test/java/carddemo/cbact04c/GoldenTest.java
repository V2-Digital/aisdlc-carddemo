package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private static final Path ASCII = Fx.ROOT.resolve("app/data/ASCII");
    private static final Path GOLD = Fx.ROOT.resolve("legacy/golden");

    private void check(Fx fx, String scenario, int records) throws IOException {
        fx.run();
        Path g = GOLD.resolve(scenario);
        assertEquals(Files.readString(g.resolve("TRANSACT.txt")), fx.get("TRANSACT"), "TRANSACT");
        assertEquals(Files.readString(g.resolve("ACCTFILE.txt")), fx.get("ACCTFILE"), "ACCTFILE");
        assertEquals(Files.readString(g.resolve("SYSOUT.txt")), fx.sysout, "SYSOUT");
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(fx.rc), "RC");
        assertEquals(records, fx.lines("TRANSACT").size());
        assertEquals(50, fx.lines("ACCTFILE").size());
    }

    @Test
    void stock(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d);
        fx.copy(ASCII.resolve("tcatbal.txt"), "TCATBALF");
        fx.copy(ASCII.resolve("cardxref.txt"), "XREFFILE");
        fx.copy(ASCII.resolve("discgrp.txt"), "DISCGRP");
        fx.copy(ASCII.resolve("acctdata.txt"), "ACCTFILE");
        check(fx, "CBACT04C", 50);
    }

    @Test
    void afterCbtrn02c(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d);
        fx.copy(GOLD.resolve("CBTRN02C/TCATBALF.txt"), "TCATBALF");
        fx.copy(ASCII.resolve("cardxref.txt"), "XREFFILE");
        fx.copy(ASCII.resolve("discgrp.txt"), "DISCGRP");
        fx.copy(GOLD.resolve("CBTRN02C/ACCTFILE.txt"), "ACCTFILE");
        check(fx, "CBACT04C-after-CBTRN02C", 50);
    }
}
