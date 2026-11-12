package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClockInjectionTest {
    @TempDir Path tmp;

    @Test
    void parmAndClockDriveIdAndTimestamps() throws Exception {
        Fixture f = new Fixture(tmp);
        f.acct = Fixture.account("00000000001", "00000001000{", "") + "\n";
        f.tcat = Fixture.tcat("00000000001", "01", "0001", "0000151277{") + "\n";
        f.xref = Fixture.xref("1111222233334444", "00000000001") + "\n";
        f.disc = Fixture.disc("DEFAULT", "01", "0001", "000150").substring(0, 22) + "\n";
        f.parm = "2023010100";
        f.clock = Cbact04c.fixedClock("2023010112345678");
        f.run();
        String t = f.lines("TRANSACT.txt", 350).get(0);
        assertEquals("2023010100000001", t.substring(0, 16));
        assertEquals("2023-01-01-12.34.56.780000", t.substring(278, 304));
        assertEquals("2023-01-01-12.34.56.780000", t.substring(304, 330));
    }
}
