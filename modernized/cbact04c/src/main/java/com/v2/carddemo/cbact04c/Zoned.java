package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned-decimal codec with trailing overpunch sign ({,A-I = +0..9; },J-R = -0..9). */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {
    }

    /** Decodes a signed zoned field with the given number of implied decimals. */
    static BigDecimal decode(String field, int scale) {
        if (field.isEmpty()) {
            throw new NumberFormatException("empty numeric field");
        }
        char last = field.charAt(field.length() - 1);
        int digit;
        boolean negative = false;
        if (POS.indexOf(last) >= 0) {
            digit = POS.indexOf(last);
        } else if (NEG.indexOf(last) >= 0) {
            digit = NEG.indexOf(last);
            negative = true;
        } else if (last >= '0' && last <= '9') {
            digit = last - '0';
        } else {
            throw new NumberFormatException("bad sign byte: " + last);
        }
        String head = field.substring(0, field.length() - 1);
        for (int i = 0; i < head.length(); i++) {
            if (head.charAt(i) < '0' || head.charAt(i) > '9') {
                throw new NumberFormatException("bad digit in: " + field);
            }
        }
        BigInteger unscaled = new BigInteger(head + digit);
        return new BigDecimal(negative ? unscaled.negate() : unscaled, scale);
    }

    /** Encodes into exactly {@code width} bytes; high-order digits are dropped. Zero (even -0) is positive. */
    static String encode(BigDecimal value, int width, int scale) {
        BigInteger u = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean negative = u.signum() < 0;
        String digits = u.abs().toString();
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        }
        digits = "0".repeat(width - digits.length()) + digits;
        int d = digits.charAt(width - 1) - '0';
        boolean neg = negative && new BigInteger(digits).signum() != 0;
        return digits.substring(0, width - 1) + (neg ? NEG : POS).charAt(d);
    }

    /** Keeps the low {@code intDigits} integer digits (and the scale), preserving the sign. */
    static BigDecimal truncate(BigDecimal value, int intDigits, int scale) {
        BigInteger mod = BigInteger.TEN.pow(intDigits + scale);
        return new BigDecimal(value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue().remainder(mod), scale);
    }
}
