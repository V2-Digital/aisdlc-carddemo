package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with EBCDIC-style overpunch sign, S9(n)V99 (two implied decimals). */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a zoned field with two implied decimals. */
    static BigDecimal decode(String field) {
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean negative = n >= 0;
        int digit = negative ? n : p;
        if (digit < 0) {
            // plain digit, unsigned
            digit = Character.digit(last, 10);
        }
        BigInteger v = new BigInteger(field.substring(0, field.length() - 1) + digit);
        return new BigDecimal(negative ? v.negate() : v, 2);
    }

    /** Encodes to {@code digits} characters, truncating high-order digits like COBOL. Zero is positive. */
    static String encode(BigDecimal value, int digits) {
        BigInteger unscaled = value.setScale(2, java.math.RoundingMode.DOWN).unscaledValue();
        BigInteger mod = unscaled.abs().mod(BigInteger.TEN.pow(digits));
        boolean negative = unscaled.signum() < 0 && mod.signum() != 0;
        String s = String.format("%0" + digits + "d", mod);
        int d = s.charAt(digits - 1) - '0';
        return s.substring(0, digits - 1) + (negative ? NEG : POS).charAt(d);
    }

    /** High-order truncation of a signed value to {@code digits} total digits (scale 2). */
    static BigDecimal truncate(BigDecimal value, int digits) {
        BigInteger u = value.setScale(2, java.math.RoundingMode.DOWN).unscaledValue();
        return new BigDecimal(u.remainder(BigInteger.TEN.pow(digits)), 2);
    }
}
