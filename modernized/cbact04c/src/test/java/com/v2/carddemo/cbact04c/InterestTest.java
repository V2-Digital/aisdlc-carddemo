package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @TempDir Path tmp;

    private String amount(String bal, String rate) throws IOException {
        Fixture f = Fixture.two(tmp, bal, rate, "");
        assertEquals(0, f.run());
        return f.lines("TRANSACT").get(0).substring(132, 143);
    }

    @Test
    void truncatesWhereRoundingWouldDiffer() throws IOException {
        // 1164.67 * 15.00 / 1200 = 14.558... -> 14.55 (rounded would be 14.56)
        assertEquals(Zoned.encode(new java.math.BigDecimal("14.55"), 11), amount("1164.67", "15.00"));
    }

    @Test
    void negativeBalanceTruncatesTowardZero() throws IOException {
        // -1.00 * 15 / 1200 = -0.0125 -> -0.01
        assertEquals(Zoned.encode(new java.math.BigDecimal("-0.01"), 11), amount("-1.00", "15.00"));
        // -1234.00 * 1.00 / 1200 = -1.0283 -> -1.02
        assertEquals(Zoned.encode(new java.math.BigDecimal("-1.02"), 11), amount("-1234.00", "1.00"));
    }

    @Test
    void negativeInterestTruncatingToZeroIsPositiveZero() throws IOException {
        // -0.50 * 1.00 / 1200 = -0.0004 -> 0.00, written as '{'
        assertEquals("0000000000{", amount("-0.50", "1.00"));
    }

    @Test
    void accountBalanceGetsInterestAndCyclesZeroed() throws IOException {
        Fixture f = Fixture.two(tmp, "1164.67", "15.00", "");
        f.run();
        String a1 = f.lines("ACCTFILE").get(0);
        assertEquals(new java.math.BigDecimal("1014.55"), Zoned.decode(a1.substring(12, 24)));
        assertEquals("00000000000{", a1.substring(78, 90));
        assertEquals("00000000000{", a1.substring(90, 102));
    }

    @Test
    void overflowWrapsSilently_F2() throws IOException {
        // 999999999.99 * 9999.99 / 1200 overflows S9(9)V99: high-order digits dropped, no error
        Fixture f = Fixture.two(tmp, "999999999.99", "9999.99", "");
        assertEquals(0, f.run());
        java.math.BigDecimal exact = new java.math.BigDecimal("999999999.99").multiply(new java.math.BigDecimal("9999.99"))
                .divide(java.math.BigDecimal.valueOf(1200), 2, java.math.RoundingMode.DOWN);
        assertFalse(exact.compareTo(new java.math.BigDecimal("999999999.99")) <= 0);
        assertEquals(Zoned.truncate(exact, 11), Zoned.decode(f.lines("TRANSACT").get(0).substring(132, 143)));
    }

    @Test
    void sequenceStartsAtOneAndCountsOnlyWrittenTrans() throws IOException {
        Fixture f = Fixture.two(tmp, "10.00", "1.00", "");
        // run once and make sure seq starts at 1 and increments only for non-zero rates
        f.run();
        assertEquals("2022071800000001", f.lines("TRANSACT").get(0).substring(0, 16));
        assertEquals("2022071800000002", f.lines("TRANSACT").get(1).substring(0, 16));
    }

    @Test
    void sourceHasNoFloatingPoint() throws IOException {
        for (String s : new String[] {"InterestCalc", "Zoned"}) {
            String src = Files.readString(Path.of("src/main/java/com/v2/carddemo/cbact04c/" + s + ".java"));
            assertFalse(src.matches("(?s).*\\b(double|float)\\b.*"), s);
        }
    }
}
