package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned DISPLAY decimals with a trailing EBCDIC-style overpunch sign. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {
    }

    /** Decodes a signed field with {@code scale} implied decimals. */
    static BigDecimal decode(String field, int scale) {
        int n = field.length();
        char last = field.charAt(n - 1);
        int p = POS.indexOf(last);
        int q = NEG.indexOf(last);
        String digits;
        boolean negative = q >= 0;
        if (p >= 0 || q >= 0) {
            digits = field.substring(0, n - 1) + (char) ('0' + (p >= 0 ? p : q));
        } else {
            digits = field;
        }
        BigInteger v = new BigInteger(digits.trim());
        return new BigDecimal(negative ? v.negate() : v, scale);
    }

    /**
     * Encodes into {@code digits} characters. Extra scale is truncated toward zero and
     * high-order digits are dropped (no SIZE ERROR). A zero result is always positive.
     */
    static String encode(BigDecimal value, int digits, int scale) {
        BigInteger unscaled = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue()
                .remainder(BigInteger.TEN.pow(digits));
        boolean negative = unscaled.signum() < 0;
        String d = unscaled.abs().toString();
        d = "0".repeat(digits - d.length()) + d;
        int last = d.charAt(digits - 1) - '0';
        return d.substring(0, digits - 1) + (negative ? NEG : POS).charAt(last);
    }

    /** Unsigned numeric field, high-order digits dropped. */
    static String unsigned(long value, int digits) {
        String d = Long.toString(Math.floorMod(value, (long) Math.pow(10, digits)));
        return "0".repeat(digits - d.length()) + d;
    }
}
