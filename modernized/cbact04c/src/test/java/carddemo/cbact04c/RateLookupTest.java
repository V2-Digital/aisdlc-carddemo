package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RateLookupTest {
    private static final String MISSING = "DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n";

    @Test
    void directHit_noMessages_usesGroupRate(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "GRP1").run(); // GRP1 rate 20.00
        assertFalse(fx.sysout.contains("MISSING"));
        assertEquals("0000001666F", fx.lines("TRANSACT").get(0).substring(132, 143)); // 16.66
    }

    @Test
    void miss_fallsBackToDefault_twoMessagesPerRecord(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "").run();
        assertEquals(0, fx.rc);
        assertEquals(4, fx.sysout.split("\n").length - fx.sysout.replace(MISSING, "").split("\n").length);
        assertEquals("0000001250{", fx.lines("TRANSACT").get(0).substring(132, 143)); // 12.50 @ 15%
    }

    @Test
    void rateZero_noTransaction_totalUnchanged(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "");
        fx.put("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00000{")));
        fx.run();
        assertEquals(0, fx.lines("TRANSACT").size());
        assertTrue(fx.lines("ACCTFILE").get(0).startsWith("00000000001Y00000100000{"));
    }

    @Test
    void zeroBalanceNonZeroRate_writesZeroAmountTransaction_D3(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000000000{", "0000000000{", "").run();
        assertEquals(2, fx.lines("TRANSACT").size());
        assertEquals("0000000000{", fx.lines("TRANSACT").get(0).substring(132, 143));
    }
}
