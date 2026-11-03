package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void negativeZeroEncodesAsPositiveZero() {
        assertEquals("00000000000{", Zoned.encode(new BigDecimal("-0.00"), 12));
        assertEquals("00000000000{", Zoned.encode(new BigDecimal("-0.004"), 12));
    }

    @Test
    void decodesNegativeZeroAndPlainDigit() {
        assertEquals(0, Zoned.decode("00000000000}").signum());
        assertEquals(new BigDecimal("12.34"), Zoned.decode("00000000123D"));
        assertEquals(new BigDecimal("-12.34"), Zoned.decode("00000000123M"));
        assertEquals(new BigDecimal("12.34"), Zoned.decode("000000001234"));
    }

    @Test
    void highOrderDigitsTruncated() {
        // S9(10)V99: 12 digits kept, the 13th is dropped
        assertEquals("00000000001A", Zoned.encode(new BigDecimal("100000000000.11"), 12));
    }

    @Test
    void goldenValuesRoundTrip() throws IOException {
        int n = 0;
        for (String scenario : new String[] {"CBACT04C", "CBACT04C-after-CBTRN02C", "CBTRN02C"}) {
            for (String line : Files.readAllLines(Fx.golden(scenario).resolve("ACCTFILE.txt"))) {
                for (int off : new int[] {13, 79, 91}) {
                    String f = Records.field(Records.pad(line, 300), off, 12);
                    assertEquals(f, Zoned.encode(Zoned.decode(f), 12), f);
                    n++;
                }
            }
        }
        for (String line : Files.readAllLines(Fx.golden("CBACT04C-after-CBTRN02C").resolve("TRANSACT.txt"))) {
            String f = Records.field(line, 133, 11);
            assertEquals(f, Zoned.encode(Zoned.decode(f), 11), f);
            n++;
        }
        System.out.println("zoned round-trips checked: " + n);
    }

    @Test
    void balanceOverflowWrapsModulo10to10() {
        // 9999999999.99 + 14.56 = 10000000014.55 -> high digit dropped -> 14.55
        BigDecimal sum = Zoned.decode("99999999999I").add(new BigDecimal("14.56"));
        assertEquals("00000000145E", Zoned.encode(sum, 12));
    }
}
