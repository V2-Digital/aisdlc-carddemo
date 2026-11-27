package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with EBCDIC-style overpunch sign: {A-I = +0..9, }J-R = -0..9. Implied scale 2. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Parses a signed zoned field with two implied decimals. */
    static BigDecimal parse(String field) {
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean neg = n >= 0;
        int digit = neg ? n : p >= 0 ? p : Character.digit(last, 10);
        String digits = field.substring(0, field.length() - 1) + digit;
        BigDecimal v = new BigDecimal(new BigInteger(digits.trim()), 2);
        return neg ? v.negate() : v;
    }

    /** Formats as a signed zoned field of {@code width} bytes, dropping high-order digits on overflow. */
    static String format(BigDecimal value, int width) {
        BigInteger unscaled = value.movePointRight(2).toBigInteger();
        String digits = unscaled.abs().toString();
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        }
        digits = "0".repeat(width - digits.length()) + digits;
        int d = digits.charAt(width - 1) - '0';
        char sign = (unscaled.signum() < 0 ? NEG : POS).charAt(d);
        return digits.substring(0, width - 1) + sign;
    }

    /** Keeps the low {@code digits} integer digits (plus 2 decimals) and the sign, like a COBOL store without SIZE ERROR. */
    static BigDecimal truncate(BigDecimal value, int digits) {
        BigDecimal limit = BigDecimal.TEN.pow(digits);
        return value.remainder(limit);
    }
}
