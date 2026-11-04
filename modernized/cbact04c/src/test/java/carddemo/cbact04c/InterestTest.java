package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC5: interest = trunc(bal * rate / 1200), 2dp, toward zero (D2). */
class InterestTest {
    @TempDir Path tmp;

    /** Runs one account with one TCATBAL record under the DEFAULT group and returns the transaction amount. */
    private String amount(String balAmt, String rateAmt) throws IOException {
        Path in = Fx.stage(tmp.resolve("in"),
                List.of(Fx.bal("00000000001", "010001", Zoned.encode(new java.math.BigDecimal(balAmt), 9))),
                List.of(Fx.xref("4111111111111111", "00000000001")),
                List.of(Fx.acct("00000000001", "0000000100{", "00000000000{", "00000000000{", "")),
                List.of(Fx.disc("DEFAULT", "010001", Zoned.encode(new java.math.BigDecimal(rateAmt), 4))));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(0, r.rc());
        List<String> t = Fx.lines(r.out().resolve("TRANSACT.txt"));
        assertEquals(1, t.size());
        return t.get(0).substring(132, 143);
    }

    @Test
    void exact() throws IOException {
        assertEquals(Zoned.encode(new java.math.BigDecimal("12.50"), 9), amount("1000.00", "15.00"));
    }

    @Test
    void fractionIsTruncatedNotRounded() throws IOException {
        // 1234.56 x 15 / 1200 = 15.432 -> 15.43
        assertEquals(Zoned.encode(new java.math.BigDecimal("15.43"), 9), amount("1234.56", "15.00"));
        // 1239.99 x 15 / 1200 = 15.499875 -> 15.49 (D2: rounding would give 15.50)
        assertEquals(Zoned.encode(new java.math.BigDecimal("15.49"), 9), amount("1239.99", "15.00"));
    }

    @Test
    void negativeBalanceTruncatesTowardZero() throws IOException {
        // -99.13 x 24 / 1200 = -1.9826 -> -1.98
        assertEquals("0000000019Q", amount("-99.13", "24.00"));
    }
}
