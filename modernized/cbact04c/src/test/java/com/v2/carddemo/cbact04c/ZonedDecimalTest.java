package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedDecimalTest {
    @Test
    void roundTripsAllTwentySignCharacters() {
        String pos = "{ABCDEFGHI";
        String neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            for (int s = 0; s < 2; s++) {
                char c = (s == 0 ? pos : neg).charAt(d);
                String field = "0000000012" + c; // 11 chars, 2 decimals
                BigDecimal v = ZonedDecimal.decode(field, 2);
                BigDecimal expect = new BigDecimal("12" + d).movePointLeft(2).movePointLeft(0);
                // digits are 0000000012 followed by d => 12d / 100 ... unscaled is 12*10+d
                expect = BigDecimal.valueOf(120 + d, 2);
                if (s == 1) {
                    expect = expect.negate();
                }
                assertEquals(0, expect.compareTo(v), "decode " + c);
                // -0 decodes to 0 and re-encodes as '{', the only non-round-trippable character
                String enc = ZonedDecimal.encode(v, 11, 2);
                if (c == '}') {
                    assertEquals("0000000012{".length(), enc.length());
                } else {
                    assertEquals(field, enc, "encode " + c);
                }
            }
        }
    }

    @Test
    void negativeZeroDecodesToZeroAndEncodesPositive() {
        assertEquals("0000000000{", ZonedDecimal.encode(ZonedDecimal.decode("0000000000}", 2), 11, 2));
    }

    @Test
    void highOrderDigitsAreTruncated() {
        assertEquals("0000000000{", ZonedDecimal.encode(new BigDecimal("1000000000.00"), 11, 2));
    }

    @Test
    void encodeTruncatesTowardZero() {
        assertEquals("0000000292M", ZonedDecimal.encode(new BigDecimal("-29.249625"), 11, 2));
    }
}
