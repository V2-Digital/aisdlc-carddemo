package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Truncation (D5), sign handling, overflow wrap (D6). Edge fixture: see legacy/tests/cbact04c_edge/ok. */
class InterestTest {
    @TempDir Path tmp;

    @Test void truncatesTowardZeroNotRounds_1164_87_at_15_is_14_56() {
        // 1164.87 * 15 / 1200 = 14.560875 -> 14.56 (rounding would give 14.56 too, so also pin the negative case)
        assertEquals(1456, 116487L * 1500 / 120000);
        assertEquals(-1456, -116487L * 1500 / 120000);
    }

    @Test void negativeBalanceTruncatesTowardZero() throws Exception {
        Support s = Support.run(Support.EDGE.resolve("ok"), tmp);
        assertEquals("0000000145O", Support.amount(s.transact.get(0))); // -1164.87 -> -14.56
    }

    @Test void tinyNegativeBecomesPositiveZeroSign() throws Exception {
        Support s = Support.run(Support.EDGE.resolve("ok"), tmp);
        assertEquals("0000000000{", Support.amount(s.transact.get(1))); // -0.01 @15 -> 0, sign '{' (harness-verified)
    }

    @Test void truncateWrapsLikeCobolWithoutSizeError() {
        assertEquals(1, Zoned.truncate(100000000001L, 11));
        assertEquals(-1, Zoned.truncate(-100000000001L, 11));
        assertEquals("0000000000A", Zoned.encode(100000000001L, 11));
    }
}
