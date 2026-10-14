package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** EBCDIC-style overpunch zoned decimals ({ A-I = +0..9, } J-R = -0..9). */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a signed zoned field with {@code scale} implied decimals. */
    static BigDecimal decode(String s, int scale) {
        char last = s.charAt(s.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean neg = n >= 0;
        int d = neg ? n : p >= 0 ? p : Character.digit(last, 10);
        if (d < 0) d = 0;
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + d);
        return new BigDecimal(neg ? v.negate() : v, scale);
    }

    /** Encodes {@code v} (already fitted) as a signed zoned field of {@code len} digits. */
    static String encode(BigDecimal v, int scale, int len) {
        BigInteger u = v.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean neg = u.signum() < 0;
        String digits = u.abs().toString();
        StringBuilder sb = new StringBuilder();
        for (int i = digits.length(); i < len; i++) sb.append('0');
        sb.append(digits);
        char last = sb.charAt(len - 1);
        sb.setCharAt(len - 1, (neg ? NEG : POS).charAt(last - '0'));
        return sb.toString();
    }

    /** Keeps the low {@code intDigits} integer digits, sign kept (no size error). */
    static BigDecimal fit(BigDecimal v, int intDigits) {
        BigInteger mod = BigInteger.TEN.pow(intDigits + v.scale());
        BigInteger u = v.unscaledValue();
        BigInteger r = u.abs().mod(mod);
        return new BigDecimal(u.signum() < 0 ? r.negate() : r, v.scale());
    }
}
