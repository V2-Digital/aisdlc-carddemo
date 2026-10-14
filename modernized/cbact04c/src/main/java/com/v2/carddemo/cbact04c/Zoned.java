package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Zoned DISPLAY decimals with trailing EBCDIC-style overpunch sign. */
public final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decode a signed field with {@code scale} implied decimals. */
    public static BigDecimal decode(String field, int scale) {
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean negative = n >= 0;
        int digit = negative ? n : p;
        if (digit < 0) {
            // unsigned digit or unknown char: treat as plain digit
            digit = Character.digit(last, 10);
            if (digit < 0) {
                digit = 0;
            }
        }
        String digits = field.substring(0, field.length() - 1) + digit;
        BigInteger v = new BigInteger(digits.isBlank() ? "0" : digits.replace(' ', '0'));
        if (negative) {
            v = v.negate();
        }
        return new BigDecimal(v, scale);
    }

    /** Truncate to the PIC: extra decimals dropped, high-order digits dropped (no SIZE ERROR). */
    public static BigDecimal truncate(BigDecimal v, int intDigits, int scale) {
        BigDecimal t = v.setScale(scale, RoundingMode.DOWN);
        BigInteger unscaled = t.unscaledValue();
        BigInteger mod = BigInteger.TEN.pow(intDigits + scale);
        BigInteger r = unscaled.abs().mod(mod);
        return new BigDecimal(unscaled.signum() < 0 ? r.negate() : r, scale);
    }

    /** Encode as signed S9(intDigits)V9(scale). Zero is always positive ({). */
    public static String encode(BigDecimal v, int intDigits, int scale) {
        BigDecimal t = truncate(v, intDigits, scale);
        int width = intDigits + scale;
        String digits = String.format("%0" + width + "d", t.unscaledValue().abs());
        int last = digits.charAt(width - 1) - '0';
        char sign = t.signum() < 0 ? NEG.charAt(last) : POS.charAt(last);
        return digits.substring(0, width - 1) + sign;
    }
}
