package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscGrpTest {
    private InterestCalc run(Path t, List<String> disc) throws Exception {
        Path d = Fx.data(t.resolve("in"), List.of(Fx.tcat("00000000001", "01", "0001", "100.00")),
                List.of(Fx.xref("4111111111111111", "00000000001")), disc,
                List.of(Fx.acct("00000000001", "0", "GOLD")));
        return Fx.calc(d);
    }

    @Test void groupPresentIsUsed(@TempDir Path t) throws Exception {
        InterestCalc c = run(t, List.of(Fx.disc("GOLD", "01", "0001", "24.00"), Fx.disc("DEFAULT", "01", "0001", "12.00")));
        assertEquals(0, c.run(t.resolve("out")));
        assertEquals("0000000020{", c.transact().get(0).substring(132, 143)); // 100*24/1200 = 2.00
        assertFalse(c.sysout().contains("DISCLOSURE GROUP RECORD MISSING"));
    }

    @Test void missingGroupFallsBackToDefault(@TempDir Path t) throws Exception {
        InterestCalc c = run(t, List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")));
        assertEquals(0, c.run(t.resolve("out")));
        assertEquals("0000000010{", c.transact().get(0).substring(132, 143)); // 1.00
        assertTrue(c.sysout().containsAll(List.of("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE")));
    }

    @Test void missingDefaultAbends16(@TempDir Path t) throws Exception {
        InterestCalc c = run(t, List.of(Fx.disc("OTHER", "01", "0001", "12.00")));
        Path out = t.resolve("out");
        assertEquals(16, c.run(out));
        assertTrue(c.sysout().contains("ERROR READING DEFAULT DISCLOSURE GROUP"));
        assertTrue(c.sysout().contains("FILE STATUS IS: NNNN0023"));
        assertTrue(c.sysout().contains("ABENDING PROGRAM"));
        assertEquals("16\n", Fx.read(out.resolve("RC.txt")));
        assertFalse(java.nio.file.Files.exists(out.resolve("TRANSACT.txt")));
        assertFalse(java.nio.file.Files.exists(out.resolve("ACCTFILE.txt")));
    }
}
