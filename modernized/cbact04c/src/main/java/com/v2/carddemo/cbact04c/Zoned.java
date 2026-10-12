package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Overpunch (EBCDIC-style) zoned decimal with two implied decimals: S9(n)V99. */
final class Zoned {
    private Zoned() {}

    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    /** Decodes a zoned field; throws NumberFormatException if it is not numeric. */
    static BigDecimal decode(String field) {
        int n = field.length();
        char last = field.charAt(n - 1);
        boolean neg = false;
        int d = POS.indexOf(last);
        if (d < 0) {
            d = NEG.indexOf(last);
            neg = d >= 0;
        }
        if (d < 0 && last >= '0' && last <= '9') {
            d = last - '0';
        }
        if (d < 0) {
            throw new NumberFormatException(field);
        }
        String head = field.substring(0, n - 1);
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException(field);
            }
        }
        BigInteger v = new BigInteger(head + d);
        return new BigDecimal(neg ? v.negate() : v, 2);
    }

    /**
     * Keeps only the low-order {@code digits} digits of the unscaled value (COBOL
     * size truncation without ON SIZE ERROR). Sign is kept.
     */
    static BigDecimal wrap(BigDecimal v, int digits) {
        BigInteger u = v.setScale(2, java.math.RoundingMode.DOWN).unscaledValue();
        return new BigDecimal(u.remainder(BigInteger.TEN.pow(digits)), 2);
    }

    /** Encodes into {@code width} chars; a zero result is always positive. */
    static String encode(BigDecimal v, int width) {
        BigInteger u = wrap(v, width).unscaledValue();
        boolean neg = u.signum() < 0;
        String s = u.abs().toString();
        s = "0".repeat(width - s.length()) + s;
        int d = s.charAt(width - 1) - '0';
        return s.substring(0, width - 1) + (neg ? NEG : POS).charAt(d);
    }
}
