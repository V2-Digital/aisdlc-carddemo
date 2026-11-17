package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal {@code S9(n)V99} with EBCDIC-style overpunch sign, as in the canonical text files. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Parses a zoned field into a BigDecimal with scale 2. */
    static BigDecimal parse(String s) {
        char last = s.charAt(s.length() - 1);
        int p = POS.indexOf(last);
        boolean neg = false;
        if (p < 0) {
            p = NEG.indexOf(last);
            neg = true;
        }
        if (p < 0) {
            throw new IllegalArgumentException("Bad zoned field: " + s);
        }
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + p);
        return new BigDecimal(neg ? v.negate() : v, 2);
    }

    /** Formats with {@code digits} total digits, silently dropping high-order digits (no SIZE ERROR). */
    static String format(BigDecimal v, int digits) {
        return format(v, digits, v.signum() < 0);
    }

    /** As {@link #format(BigDecimal, int)}, with an explicit sign (for a negative zero). */
    static String format(BigDecimal v, int digits, boolean negative) {
        BigInteger mod = BigInteger.TEN.pow(digits);
        String d = v.unscaledValue().abs().mod(mod).toString();
        d = "0".repeat(digits - d.length()) + d;
        char last = (negative ? NEG : POS).charAt(d.charAt(digits - 1) - '0');
        return d.substring(0, digits - 1) + last;
    }

    /** Keeps the low {@code digits} digits (including the 2 decimals) and the sign, like a COBOL MOVE/ADD. */
    static BigDecimal truncate(BigDecimal v, int digits) {
        BigInteger u = v.unscaledValue();
        BigInteger m = u.abs().mod(BigInteger.TEN.pow(digits));
        return new BigDecimal(u.signum() < 0 ? m.negate() : m, 2);
    }
}
