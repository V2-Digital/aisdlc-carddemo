package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with EBCDIC-style overpunch sign in the last byte. */
public final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes {@code s} (all digits, sign overpunched on the last char) with {@code scale} implied decimals. */
    public static BigDecimal decode(String s, int scale) {
        char last = s.charAt(s.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean neg = n >= 0;
        int lastDigit = p >= 0 ? p : n >= 0 ? n : Character.digit(last, 10);
        if (lastDigit < 0) {
            throw new IllegalArgumentException("bad zoned decimal: " + s);
        }
        String digits = s.substring(0, s.length() - 1) + lastDigit;
        BigDecimal v = new BigDecimal(new BigInteger(digits), scale);
        return neg ? v.negate() : v;
    }

    /** Encodes {@code v} into {@code digits} digits, truncating extra decimals and high-order digits. */
    public static String encode(BigDecimal v, int digits, int scale) {
        BigInteger unscaled = v.movePointRight(scale).toBigInteger();
        BigInteger mod = unscaled.abs().mod(BigInteger.TEN.pow(digits));
        boolean neg = unscaled.signum() < 0 && mod.signum() != 0;
        String d = String.format("%0" + digits + "d", mod);
        int last = d.charAt(digits - 1) - '0';
        return d.substring(0, digits - 1) + (neg ? NEG : POS).charAt(last);
    }

    /** Truncates {@code v} to a signed field of {@code digits} digits with {@code scale} decimals. */
    public static BigDecimal truncate(BigDecimal v, int digits, int scale) {
        return decode(encode(v, digits, scale), scale);
    }
}
