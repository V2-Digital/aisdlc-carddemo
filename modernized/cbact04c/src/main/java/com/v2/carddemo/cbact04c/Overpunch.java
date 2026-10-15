package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Signed zoned decimal (S9(n)V9(m)) with the sign overpunched on the last digit. */
public final class Overpunch {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Overpunch() {}

    public static BigDecimal decode(String field, int scale) {
        char last = field.charAt(field.length() - 1);
        int digit;
        boolean negative;
        if (POS.indexOf(last) >= 0) {
            digit = POS.indexOf(last);
            negative = false;
        } else if (NEG.indexOf(last) >= 0) {
            digit = NEG.indexOf(last);
            negative = true;
        } else if (last >= '0' && last <= '9') {
            digit = last - '0';
            negative = false;
        } else {
            throw new IllegalArgumentException("Bad sign character '" + last + "' in: " + field);
        }
        BigInteger n = new BigInteger(field.substring(0, field.length() - 1) + digit);
        return new BigDecimal(negative ? n.negate() : n, scale);
    }

    /** Encodes {@code value} (already at {@code scale}) in {@code digits} digits, dropping high-order digits on overflow. Zero is always positive. */
    public static String encode(BigDecimal value, int digits, int scale) {
        BigInteger n = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean negative = n.signum() < 0;
        BigInteger mod = n.abs().mod(BigInteger.TEN.pow(digits));
        String s = String.format("%0" + digits + "d", mod);
        if (mod.signum() == 0) negative = false;
        int d = s.charAt(digits - 1) - '0';
        return s.substring(0, digits - 1) + (negative ? NEG : POS).charAt(d);
    }

    /** Wraps a value to the integer capacity of a signed field (sign kept, high-order digits lost). */
    public static BigDecimal wrap(BigDecimal value, int digits, int scale) {
        return decode(encode(value, digits, scale), scale);
    }
}
