package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned-decimal (DISPLAY, trailing overpunch sign) codec with COBOL PIC-width truncation. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a field with {@code scale} implied decimals. A plain trailing digit is positive. */
    static BigDecimal decode(String field, int scale) {
        char last = field.charAt(field.length() - 1);
        boolean neg = NEG.indexOf(last) >= 0;
        int d = neg ? NEG.indexOf(last) : POS.indexOf(last);
        String digits = field.substring(0, field.length() - 1) + (d >= 0 ? (char) ('0' + d) : last);
        BigInteger v = new BigInteger(digits.trim());
        return new BigDecimal(neg ? v.negate() : v, scale);
    }

    /** Encodes into {@code digits} total digits; high-order digits that do not fit are dropped. */
    static String encode(BigDecimal value, int digits, int scale) {
        BigInteger unscaled = fit(value, digits, scale).unscaledValue();
        boolean neg = unscaled.signum() < 0;
        String s = unscaled.abs().toString();
        s = "0".repeat(digits - s.length()) + s;
        int last = s.charAt(digits - 1) - '0';
        return s.substring(0, digits - 1) + (neg ? NEG : POS).charAt(last);
    }

    /** COBOL store into S9(digits-scale)V(scale): truncate fraction toward zero, drop high-order digits. */
    static BigDecimal fit(BigDecimal value, int digits, int scale) {
        BigInteger u = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        BigInteger mod = BigInteger.TEN.pow(digits);
        BigInteger r = u.abs().mod(mod);
        return new BigDecimal(u.signum() < 0 ? r.negate() : r, scale);
    }
}
