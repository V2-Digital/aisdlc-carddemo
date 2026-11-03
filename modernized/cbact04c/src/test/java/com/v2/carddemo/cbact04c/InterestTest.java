package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    /** One account, one TCATBAL row (01/0001) with the given balance; DEFAULT rate is 15.00. Returns the TRANSACT amount field. */
    private String amountFor(Path tmp, String balance) throws IOException {
        Path in = Files.createDirectory(tmp.resolve("in"));
        Fx.stage(in, false);
        String row = "00000000001" + "01" + "0001" + Zoned.encode(new BigDecimal(balance), 11);
        Files.writeString(in.resolve("TCATBALF.txt"), Records.pad(row, 50) + "\n");
        Path out = tmp.resolve("out");
        assertEquals(0, Fx.run(in, out));
        List<String> t = Files.readAllLines(out.resolve("TRANSACT.txt"));
        assertEquals(1, t.size());
        return Records.field(t.get(0), 133, 11);
    }

    @Test
    void truncatesNotRounds_14_560875(@TempDir Path tmp) throws IOException {
        // 1164.87 * 15.00 / 1200 = 14.560875 -> 14.56
        assertEquals("0000000145F", amountFor(tmp, "1164.87"));
    }

    @Test
    void truncationDiffersFromRounding(@TempDir Path tmp) throws IOException {
        // 1165.50 * 15.00 / 1200 = 14.56875: truncation 14.56, HALF_UP would give 14.57
        assertEquals("0000000145F", amountFor(tmp, "1165.50"));
        BigDecimal exact = new BigDecimal("1165.50").multiply(new BigDecimal("15.00"));
        assertEquals(new BigDecimal("14.57"), exact.divide(new BigDecimal("1200"), 2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void negativeTruncatesTowardZero_toPositiveZero(@TempDir Path tmp) throws IOException {
        // -0.60 * 15 / 1200 = -0.0075 -> 0.00, encoded +0
        assertEquals("0000000000{", amountFor(tmp, "-0.60"));
    }

    @Test
    void negativeInterestKeepsSign(@TempDir Path tmp) throws IOException {
        // -1164.87 -> -14.56 -> 'O' = -6
        assertEquals("0000000145O", amountFor(tmp, "-1164.87"));
    }
}
