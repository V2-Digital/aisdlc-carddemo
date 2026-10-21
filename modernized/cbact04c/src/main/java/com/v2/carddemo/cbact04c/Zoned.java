package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with trailing overpunch sign ({ A-I positive, } J-R negative), scale 2. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    static BigDecimal parse(String field, int scale) {
        int n = field.length();
        char last = field.charAt(n - 1);
        boolean neg = false;
        int digit;
        int p = POS.indexOf(last);
        int q = NEG.indexOf(last);
        if (p >= 0) {
            digit = p;
        } else if (q >= 0) {
            digit = q;
            neg = true;
        } else {
            digit = last - '0';
        }
        BigInteger v = new BigInteger(field.substring(0, n - 1) + digit);
        return new BigDecimal(neg ? v.negate() : v, scale);
    }

    /** Keeps the low-order {@code digits} digits (COBOL truncation, no SIZE ERROR). */
    static BigDecimal truncate(BigDecimal v, int digits) {
        BigInteger u = v.unscaledValue();
        BigInteger m = u.abs().mod(BigInteger.TEN.pow(digits));
        return new BigDecimal(u.signum() < 0 ? m.negate() : m, v.scale());
    }

    static String format(BigDecimal v, int digits) {
        BigInteger u = v.unscaledValue();
        String s = u.abs().mod(BigInteger.TEN.pow(digits)).toString();
        s = "0".repeat(digits - s.length()) + s;
        int d = s.charAt(digits - 1) - '0';
        boolean neg = u.signum() < 0 && !s.chars().allMatch(c -> c == '0');
        return s.substring(0, digits - 1) + (neg ? NEG : POS).charAt(d);
    }
}
