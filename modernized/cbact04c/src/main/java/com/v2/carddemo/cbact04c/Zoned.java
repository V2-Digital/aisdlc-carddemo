package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned-decimal (trailing overpunch) codec and fixed-width helpers. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decode a signed zoned field with an implied scale of 2. */
    static BigDecimal decode(String field) {
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        String digits = field.substring(0, field.length() - 1);
        int d;
        boolean neg = false;
        if (p >= 0) {
            d = p;
        } else if (n >= 0) {
            d = n;
            neg = true;
        } else {
            d = Character.digit(last, 10);
        }
        BigInteger v = new BigInteger(digits + d);
        return new BigDecimal(neg ? v.negate() : v, 2);
    }

    /** Encode with the sign overpunched on the last digit; zero is always '{'. */
    static String encode(BigDecimal value, int width) {
        BigInteger u = value.unscaledValue();
        String digits = pad(u.abs().toString(), width, '0', true);
        char sign = (u.signum() < 0 ? NEG : POS).charAt(digits.charAt(width - 1) - '0');
        return digits.substring(0, width - 1) + sign;
    }

    /** High-order truncation to {@code digits} integer+fraction digits, sign kept. */
    static BigDecimal truncate(BigDecimal value, int digits) {
        return new BigDecimal(value.unscaledValue().remainder(BigInteger.TEN.pow(digits)), value.scale());
    }

    static String pad(String s, int width, char fill, boolean left) {
        if (s.length() >= width) {
            return left ? s.substring(s.length() - width) : s.substring(0, width);
        }
        String f = String.valueOf(fill).repeat(width - s.length());
        return left ? f + s : s + f;
    }

    static String right(String s, int width) {
        return pad(s, width, ' ', false);
    }
}
