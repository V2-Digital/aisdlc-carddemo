package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with EBCDIC-style overpunch sign: '{' 'A'-'I' positive, '}' 'J'-'R' negative. */
public final class ZonedDecimal {
    private ZonedDecimal() {}

    /** Decode a zoned field with {@code scale} implied decimals. */
    public static BigDecimal decode(String field, int scale) {
        int n = field.length();
        char last = field.charAt(n - 1);
        int digit;
        boolean negative;
        if (last == '{') {
            digit = 0;
            negative = false;
        } else if (last == '}') {
            digit = 0;
            negative = true;
        } else if (last >= 'A' && last <= 'I') {
            digit = last - 'A' + 1;
            negative = false;
        } else if (last >= 'J' && last <= 'R') {
            digit = last - 'J' + 1;
            negative = true;
        } else if (last >= '0' && last <= '9') {
            digit = last - '0';
            negative = false;
        } else {
            throw new IllegalArgumentException("bad zoned decimal: " + field);
        }
        BigInteger unscaled = new BigInteger(field.substring(0, n - 1) + digit);
        return new BigDecimal(negative ? unscaled.negate() : unscaled, scale);
    }

    /**
     * Encode to {@code width} bytes. Low-order digits are kept when the value is too wide
     * (COBOL MOVE truncation). Zero is always positive ('{').
     */
    public static String encode(BigDecimal value, int scale, int width) {
        BigInteger unscaled = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean negative = unscaled.signum() < 0;
        BigInteger mod = BigInteger.TEN.pow(width);
        BigInteger kept = unscaled.abs().mod(mod);
        if (kept.signum() == 0) {
            negative = false;
        }
        String digits = String.format("%0" + width + "d", kept);
        int d = digits.charAt(width - 1) - '0';
        char overpunch = negative ? (d == 0 ? '}' : (char) ('J' + d - 1)) : (d == 0 ? '{' : (char) ('A' + d - 1));
        return digits.substring(0, width - 1) + overpunch;
    }

    /** Keep only the low-order {@code intDigits} integer digits of {@code v} (sign preserved, 2 dp). */
    public static BigDecimal truncate(BigDecimal v, int intDigits) {
        return decode(encode(v, 2, intDigits + 2), 2);
    }
}
