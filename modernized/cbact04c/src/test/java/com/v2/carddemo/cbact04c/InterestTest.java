package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    private String amount(Path tmp, String bal, String rate) throws Exception {
        Path d = Fx.data(tmp.resolve("in"), List.of(Fx.tcat("00000000001", "01", "0001", bal)),
                List.of(Fx.xref("4111111111111111", "00000000001")),
                List.of(Fx.disc("DEFAULT", "01", "0001", rate)),
                List.of(Fx.acct("00000000001", "0", "")));
        InterestCalc c = Fx.calc(d);
        assertEquals(0, c.run(tmp.resolve("out")));
        assertEquals(1, c.transact().size());
        return c.transact().get(0).substring(16 + 2 + 4 + 10 + 100, 16 + 2 + 4 + 10 + 100 + 11);
    }

    /** Half-up would give 0.01; COBOL truncates to 0.00. */
    @Test void truncationNotRounding(@TempDir Path t) throws Exception {
        assertEquals("0000000000{", amount(t, "0.40", "15.00"));
    }
    @Test void negativeSmallTruncatesToPositiveZero(@TempDir Path t) throws Exception {
        assertEquals("0000000000{", amount(t, "-0.40", "15.00"));
    }
    @Test void goldenCase(@TempDir Path t) throws Exception {
        assertEquals("0000000243B", amount(t, "1945.87", "15.00")); // 24.32 (24.3234 truncated)
    }
    @Test void negativeBalanceGivesNegativeInterest_D7(@TempDir Path t) throws Exception {
        assertEquals("0000000000J", amount(t, "-1.00", "15.00")); // -0.0125 -> -0.01
    }
}
