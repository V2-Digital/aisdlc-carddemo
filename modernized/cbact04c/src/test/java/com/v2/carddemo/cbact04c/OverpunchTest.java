package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OverpunchTest {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    @Test
    void allTwentySignCharsDecodeAndEncodeBack() {
        // every PIC used: S9(9)V99 (11), S9(10)V99 (12), S9(4)V99 (6)
        for (int len : new int[] {11, 12, 6}) {
            for (int d = 0; d < 10; d++) {
                String prefix = "0".repeat(len - 2) + "1";
                String p = prefix + POS.charAt(d);
                String n = prefix + NEG.charAt(d);
                BigDecimal pv = new BigDecimal("1" + d).movePointLeft(2).setScale(2);
                assertEquals(pv.setScale(2), Overpunch.decode(p, 2));
                BigDecimal nv = d == 0 ? new BigDecimal("-10.00") : pv.negate();
                assertEquals(new BigDecimal("1" + d).movePointLeft(2).negate().setScale(2), Overpunch.decode(n, 2));
                assertEquals(p, Overpunch.encode(Overpunch.decode(p, 2), len, 2));
                assertEquals(n, Overpunch.encode(Overpunch.decode(n, 2), len, 2));
            }
        }
    }

    @Test
    void zeroEncodesAsPositiveBrace_negativeZeroDecodesToZero() {
        assertEquals("0000000000{", Overpunch.encode(BigDecimal.ZERO, 11, 2));
        assertEquals(0, Overpunch.decode("0000000000}", 2).signum());
        assertEquals("0000000000{", Overpunch.encode(Overpunch.decode("0000000000}", 2), 11, 2));
    }

    @Test
    void knownGoldenValues() {
        assertEquals(new BigDecimal("14.56"), Overpunch.decode("0000000145F", 2));
        assertEquals(new BigDecimal("18.77"), Overpunch.decode("0000000187G", 2));
        assertEquals("0000000187G", Overpunch.encode(new BigDecimal("18.77"), 11, 2));
    }

    @Test
    void plainDigitsAreAcceptedAsPositive() {
        assertEquals(new BigDecimal("12.34"), Overpunch.decode("001234", 2));
    }

    @Test
    void unsignedFields() {
        assertEquals("000042", Overpunch.encodeUnsigned(42, 6));
        assertEquals(new BigDecimal("42"), Overpunch.decodeUnsigned("00042", 0));
        assertThrows(IllegalArgumentException.class, () -> Overpunch.decodeUnsigned("0004{", 0));
    }

    @Test
    void invalidInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> Overpunch.decode("00000000X0Z", 2));
        assertThrows(IllegalArgumentException.class, () -> Overpunch.decode("", 2));
        assertThrows(IllegalArgumentException.class, () -> Overpunch.decode("0A00000001{", 2));
    }

    @Test
    void highOrderTruncation() {
        assertEquals(new BigDecimal("123.45"), Overpunch.truncate(new BigDecimal("1000000123.45"), 11, 2));
        assertEquals(new BigDecimal("-0.12"), Overpunch.truncate(new BigDecimal("-0.1251"), 11, 2));
    }
}
