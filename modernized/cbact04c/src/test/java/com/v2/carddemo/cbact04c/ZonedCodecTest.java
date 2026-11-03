package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ZonedCodecTest {
    @Test
    void roundTripAllOverpunchChars() {
        String pos = "{ABCDEFGHI", neg = "}JKLMNOPQR";
        for (int d = 0; d < 10; d++) {
            assertEquals(d, Zoned.decode("000" + pos.charAt(d)));
            assertEquals(-d, Zoned.decode("000" + neg.charAt(d)));
            if (d != 0) {
                assertEquals("000" + pos.charAt(d), Zoned.encode(d, 4));
                assertEquals("000" + neg.charAt(d), Zoned.encode(-d, 4));
            }
        }
    }

    @Test
    void negativeZeroReadsAsZero() {
        assertEquals(0, Zoned.decode("0000}"));
    }

    @Test
    void zeroIsWrittenWithBrace() {
        assertEquals("0000{", Zoned.encode(0, 5));
    }

    @Test
    void multiDigitValues() {
        assertEquals(1456, Zoned.decode("000000001456".substring(0, 11) + "6"));
        assertEquals("0000000145O".substring(0, 10) + "O", Zoned.encode(-1455, 11).substring(0, 10) + "O");
        assertEquals(-1455, Zoned.decode("0000000145N".substring(0, 10) + "N"));
    }

    @Test
    void encodeKeepsLowOrderDigits() {
        assertEquals("2345{", Zoned.encode(123450, 5));
    }

    @Test
    void wrapAtNineAndTenIntegerDigits() {
        // S9(9)V99 = 11 digits, S9(10)V99 = 12 digits
        assertEquals(0, Zoned.wrap(100_000_000_000L, 11));
        assertEquals(1, Zoned.wrap(100_000_000_001L, 11));
        assertEquals(-1, Zoned.wrap(-100_000_000_001L, 11));
        assertEquals(5, Zoned.wrap(1_000_000_000_005L, 12));
        assertEquals(99_999_999_999L, Zoned.wrap(99_999_999_999L, 11));
    }
}
