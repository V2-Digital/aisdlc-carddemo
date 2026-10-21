package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with trailing overpunch sign ({ A-I = +0..9, } J-R = -0..9). */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Parses a zoned field with {@code scale} implied decimals. A plain last digit is positive. */
    static BigDecimal parse(String field, int scale) {
        char last = field.charAt(field.length() - 1);
        int digit;
        boolean negative = false;
        if (Character.isDigit(last)) {
            digit = last - '0';
        } else if (POS.indexOf(last) >= 0) {
            digit = POS.indexOf(last);
        } else if (NEG.indexOf(last) >= 0) {
            digit = NEG.indexOf(last);
            negative = true;
        } else {
            throw new IllegalArgumentException("bad zoned field: " + field);
        }
        BigInteger n = new BigInteger(field.substring(0, field.length() - 1) + digit);
        return new BigDecimal(negative ? n.negate() : n, scale);
    }

    /** Formats to {@code digits} digits (scale from the value), truncating high-order digits. Zero is '{'. */
    static String format(BigDecimal value, int digits) {
        BigInteger unscaled = value.unscaledValue();
        boolean negative = unscaled.signum() < 0;
        BigInteger mag = unscaled.abs().mod(BigInteger.TEN.pow(digits));
        String s = String.format("%0" + digits + "d", mag);
        char last = (negative && mag.signum() != 0 ? NEG : POS).charAt(s.charAt(digits - 1) - '0');
        return s.substring(0, digits - 1) + last;
    }

    /** Truncates a value to S9(intDigits)V(scale): drops extra decimals toward zero, then high-order digits. */
    static BigDecimal truncate(BigDecimal value, int intDigits, int scale) {
        BigInteger mag = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean negative = mag.signum() < 0;
        mag = mag.abs().mod(BigInteger.TEN.pow(intDigits + scale));
        return new BigDecimal(negative ? mag.negate() : mag, scale);
    }
}
