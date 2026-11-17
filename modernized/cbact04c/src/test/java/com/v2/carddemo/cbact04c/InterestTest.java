package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Amount field (cols 133-143) of the single interest transaction. */
class InterestTest {
    @TempDir Path tmp;

    private String amount(String bal, String rate) throws IOException {
        Fx.Result r = Fx.simple(tmp,
                List.of(Fx.tcat("00000000001", "01", "0001", bal)),
                List.of(Fx.acct("00000000001", "100.00", "0", "0", "G")),
                List.of(Fx.disc("DEFAULT", "01", "0001", rate)));
        assertEquals(0, r.rc());
        return r.records("TRANSACT.txt").get(0).substring(132, 143);
    }

    @Test void truncatesFraction() throws IOException { assertEquals("0000000145F", amount("1164.87", "15.00")); }
    @Test void negativeTruncatesTowardZero() throws IOException { assertEquals("0000000012N", amount("-100.00", "15.00")); }
    @Test void tinyNegativeSignOfZero() throws IOException { assertEquals("0000000000{", amount("-0.01", "15.00")); }
    @Test void remainderDropped() throws IOException { assertEquals("0000000000{", amount("0.79", "1.00")); }
    @Test void overflowWraps() throws IOException {
        // 999999999.99 * 9999.99 / 1200 ~ 8.3e9 -> high-order digits dropped (D4)
        java.math.BigDecimal exact = new java.math.BigDecimal("999999999.99").multiply(new java.math.BigDecimal("9999.99"))
                .divide(java.math.BigDecimal.valueOf(1200), 2, java.math.RoundingMode.DOWN);
        assertTrue(exact.compareTo(new java.math.BigDecimal("999999999.99")) > 0);
        assertEquals(Zoned.encode(exact.remainder(java.math.BigDecimal.TEN.pow(9)), 11), amount("999999999.99", "9999.99"));
    }
}
