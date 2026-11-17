package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned-decimal (overpunch) codec for {@code S9(n)V99} fields, as the harness unloads them. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a signed zoned field with two implied decimals. {@code -0} reads as 0. */
    static BigDecimal decode(String s) {
        char last = s.charAt(s.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        if (p < 0 && n < 0) {
            throw new IllegalArgumentException("bad zoned field: " + s);
        }
        String digits = s.substring(0, s.length() - 1) + (char) ('0' + (p >= 0 ? p : n));
        BigDecimal v = new BigDecimal(new BigInteger(digits), 2);
        return n >= 0 ? v.negate() : v;
    }

    /** Encodes to {@code width} bytes, dropping high-order digits like a COBOL MOVE/COMPUTE. */
    static String encode(BigDecimal v, int width) {
        BigInteger units = v.movePointRight(2).toBigInteger();
        boolean neg = units.signum() < 0;
        String digits = units.abs().toString();
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        }
        digits = "0".repeat(width - digits.length()) + digits;
        char sign = (neg ? NEG : POS).charAt(digits.charAt(width - 1) - '0');
        return digits.substring(0, width - 1) + sign;
    }

    /** Keeps the low {@code intDigits} integer digits (and the two decimals), preserving the sign. */
    static BigDecimal wrap(BigDecimal v, int intDigits) {
        BigDecimal mod = BigDecimal.TEN.pow(intDigits);
        return v.remainder(mod);
    }
}
