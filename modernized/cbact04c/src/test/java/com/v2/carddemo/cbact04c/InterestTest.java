package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @TempDir Path tmp;

    @Test
    void zonedRoundTrip() {
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.00"), 11, 2));
        assertEquals("0000000001K", Zoned.encode(new BigDecimal("-0.12"), 11, 2));
        assertEquals("0000000187G", Zoned.encode(new BigDecimal("18.77"), 11, 2));
        assertEquals(new BigDecimal("-0.12"), Zoned.decode("0000000001K", 2));
        assertEquals(new BigDecimal("18.77"), Zoned.decode("0000000187G", 2));
        assertEquals(new BigDecimal("0.00"), Zoned.decode("0000000000}", 2));
    }

    @Test
    void overflowDropsHighOrderDigits() {
        // 1,000,000,000.12 does not fit S9(9)V99: the leading 1 is dropped
        assertEquals("0000000001B", Zoned.encode(new BigDecimal("1000000000.12"), 11, 2));
    }

    /** Runs one account, one category, then a second dummy account so the first is rewritten. */
    private String interestFor(String bal, String rate) throws IOException {
        Path in = Fx.dir(tmp, "i" + bal + rate,
                List.of(Fx.tcat("00000000001", "01", "0001", bal), Fx.tcat("00000000002", "01", "0001", "0")),
                List.of(Fx.xref("1111111111111111", "00000000001"), Fx.xref("2222222222222222", "00000000002")),
                List.of(Fx.disc("DEFAULT", "01", "0001", rate)),
                List.of(Fx.acct("00000000001", "0", "0", "0", ""), Fx.acct("00000000002", "0", "0", "0", "")));
        Fx.Run r = Fx.run(in, tmp.resolve("o" + bal + rate));
        return r.lines("TRANSACT").get(0).substring(132, 143);
    }

    @Test
    void truncatesPositiveTowardZero() throws IOException {
        // 10.07 * 15 / 1200 = 0.125875 -> 0.12 (half-up would also be 0.13 at 0.1259? 0.13)
        assertEquals("0000000001B", interestFor("10.07", "15"));
        // 1288.10 * 15 / 1200 = 16.10125 -> 16.10 ; 100.00 * 0.01 / 1200 -> 0
        assertEquals("0000000161{", interestFor("1288.10", "15"));
        // 0.99 * 15 / 1200 = 0.012375 -> 0.01 ; 79.99*15/1200=0.99987 -> 0.99 (rounding would give 1.00)
        assertEquals("0000000009I", interestFor("79.99", "15"));
    }

    @Test
    void truncatesNegativeTowardZero() throws IOException {
        // -10.07 * 15 / 1200 = -0.125875 -> -0.12
        assertEquals("0000000001K", interestFor("-10.07", "15"));
        // -79.99 -> -0.99, not -1.00
        assertEquals("0000000009R", interestFor("-79.99", "15"));
        // tiny negative truncates to zero and is written positive
        assertEquals("0000000000{", interestFor("-0.50", "15"));
    }
}
