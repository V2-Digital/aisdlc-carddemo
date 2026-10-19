package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned-decimal (overpunch sign) helpers for S9(n)V99 fields. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Parses a zoned field with 2 implied decimals. Plain digits read as positive. */
    static BigDecimal parse(String s) {
        char last = s.charAt(s.length() - 1);
        boolean neg = false;
        int d;
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        if (p >= 0) {
            d = p;
        } else if (n >= 0) {
            d = n;
            neg = true;
        } else {
            d = last - '0';
        }
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + d);
        return new BigDecimal(neg ? v.negate() : v, 2);
    }

    /** Keeps the low {@code intDigits} integer digits (COBOL truncation, no SIZE ERROR). */
    static BigDecimal trunc(BigDecimal v, int intDigits) {
        BigInteger mod = BigInteger.TEN.pow(intDigits + 2);
        BigInteger u = v.unscaledValue().abs().mod(mod);
        return new BigDecimal(v.signum() < 0 ? u.negate() : u, 2);
    }

    /** Formats as S9(intDigits)V99 zoned; negative zero is written as positive zero. */
    static String format(BigDecimal v, int intDigits) {
        int len = intDigits + 2;
        String digits = String.format("%0" + len + "d", trunc(v, intDigits).unscaledValue().abs());
        int last = digits.charAt(len - 1) - '0';
        boolean neg = v.signum() < 0 && trunc(v, intDigits).signum() != 0;
        char sign = (neg ? NEG : POS).charAt(last);
        return digits.substring(0, len - 1) + sign;
    }
}
