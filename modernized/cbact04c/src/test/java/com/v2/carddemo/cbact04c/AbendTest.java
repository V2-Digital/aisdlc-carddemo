package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @Test void accountMissing(@TempDir Path t) throws Exception {
        Path d = Fx.data(t.resolve("in"), List.of(Fx.tcat("00000000009", "01", "0001", "1")),
                List.of(Fx.xref("4111111111111111", "00000000009")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")), List.of(Fx.acct("00000000001", "0", "")));
        InterestCalc c = Fx.calc(d);
        assertEquals(16, c.run(t.resolve("out")));
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000009", "ERROR READING ACCOUNT FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"),
                c.sysout().subList(c.sysout().size() - 4, c.sysout().size()));
    }

    @Test void xrefMissing(@TempDir Path t) throws Exception {
        Path d = Fx.data(t.resolve("in"), List.of(Fx.tcat("00000000001", "01", "0001", "1")),
                List.of(Fx.xref("4111111111111111", "00000000002")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")), List.of(Fx.acct("00000000001", "0", "")));
        InterestCalc c = Fx.calc(d);
        Path out = t.resolve("out");
        assertEquals(16, c.run(out));
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000001", "ERROR READING XREF FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"),
                c.sysout().subList(c.sysout().size() - 4, c.sysout().size()));
        assertEquals("16\n", Fx.read(out.resolve("RC.txt")));
        assertTrue(java.nio.file.Files.exists(out.resolve("SYSOUT.txt")));
        assertFalse(java.nio.file.Files.exists(out.resolve("TRANSACT.txt")));
    }

    @Test void cliExitCodeIs16(@TempDir Path t) throws Exception {
        Path d = Fx.data(t.resolve("in"), List.of(Fx.tcat("00000000009", "01", "0001", "1")),
                List.of(), List.of(), List.of());
        assertEquals(16, Main.run(new String[] {"--data", d.toString(), "--out", t.resolve("o").toString(), "--parm", "2022071800"}));
    }
}
