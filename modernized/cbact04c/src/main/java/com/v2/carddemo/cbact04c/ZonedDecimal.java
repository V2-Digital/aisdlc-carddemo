package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with trailing EBCDIC-style overpunch sign: {A-I = +0..9, }J-R = -0..9. */
public final class ZonedDecimal {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private ZonedDecimal() {}

    /** Decodes a signed zoned field with {@code scale} implied decimals. */
    public static BigDecimal decode(String field, int scale) {
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean negative = n >= 0;
        int lastDigit = negative ? n : p;
        if (lastDigit < 0) {
            lastDigit = Character.digit(last, 10); // unsigned digit
            if (lastDigit < 0) {
                throw new NumberFormatException("bad zoned decimal: '" + field + "'");
            }
        }
        BigInteger v = new BigInteger(field.substring(0, field.length() - 1) + lastDigit);
        return new BigDecimal(negative ? v.negate() : v, scale);
    }

    /** Encodes {@code value} (scale-truncated) into {@code intDigits + scale} digits, high-order digits dropped. */
    public static String encode(BigDecimal value, int intDigits, int scale) {
        BigInteger u = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean negative = u.signum() < 0;
        int width = intDigits + scale;
        BigInteger mag = u.abs().mod(BigInteger.TEN.pow(width));
        if (mag.signum() == 0) {
            negative = false;
        }
        String digits = String.format("%0" + width + "d", mag);
        char sign = (negative ? NEG : POS).charAt(digits.charAt(width - 1) - '0');
        return digits.substring(0, width - 1) + sign;
    }

    /** Keeps the low-order {@code intDigits} integer digits, preserving sign (COBOL size truncation). */
    public static BigDecimal truncate(BigDecimal value, int intDigits, int scale) {
        BigInteger u = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        return new BigDecimal(u.remainder(BigInteger.TEN.pow(intDigits + scale)), scale);
    }
}
