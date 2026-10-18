package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal S9(n)V99 with EBCDIC-style overpunch signs: {A-I = +0..9, }J-R = -0..9. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a zoned field with two implied decimals. */
    static BigDecimal decode(String field) {
        int n = field.length();
        char last = field.charAt(n - 1);
        int d = POS.indexOf(last);
        boolean neg = false;
        if (d < 0) {
            d = NEG.indexOf(last);
            neg = true;
        }
        if (d < 0) {
            throw new IllegalArgumentException("bad zoned field: " + field);
        }
        String digits = field.substring(0, n - 1) + d;
        for (int i = 0; i < n; i++) {
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9') {
                throw new IllegalArgumentException("bad zoned field: " + field);
            }
        }
        BigDecimal v = new BigDecimal(new BigInteger(digits), 2);
        return neg ? v.negate() : v;
    }

    /** Keeps the low {@code width} digits (including the 2 decimals), like a COBOL store without SIZE ERROR. */
    static BigDecimal wrap(BigDecimal v, int width) {
        BigInteger mod = BigInteger.TEN.pow(width);
        BigInteger u = v.unscaledValue().abs().mod(mod);
        return new BigDecimal(v.signum() < 0 ? u.negate() : u, 2);
    }

    /** Encodes into exactly {@code width} characters, truncating high-order digits. A negative value is always overpunched negative. */
    static String encode(BigDecimal v, int width) {
        BigInteger mag = wrap(v, width).unscaledValue().abs();
        String s = String.format("%0" + width + "d", mag);
        int d = s.charAt(width - 1) - '0';
        return s.substring(0, width - 1) + (v.signum() < 0 ? NEG : POS).charAt(d);
    }
}
