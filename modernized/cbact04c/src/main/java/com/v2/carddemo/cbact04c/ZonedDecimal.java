package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with trailing overpunch sign, as in PIC S9(n)V99. */
public final class ZonedDecimal {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private ZonedDecimal() {}

    /** Decodes a field whose last two digits are the decimal places. */
    public static BigDecimal decode(String field, int scale) {
        if (field.isEmpty()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        char last = field.charAt(field.length() - 1);
        boolean negative = false;
        int digit;
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        if (p >= 0) {
            digit = p;
        } else if (n >= 0) {
            digit = n;
            negative = true;
        } else {
            digit = Character.digit(last, 10);
        }
        String digits = field.substring(0, field.length() - 1);
        BigInteger v = new BigInteger((digits.isBlank() ? "" : digits.trim()) + Math.max(digit, 0));
        return new BigDecimal(negative ? v.negate() : v, scale);
    }

    /** Encodes into {@code width} characters; high-order digits are truncated like COBOL without SIZE ERROR. */
    public static String encode(BigDecimal value, int width, int scale) {
        BigInteger unscaled = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean negative = unscaled.signum() < 0;
        String digits = unscaled.abs().toString();
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        }
        digits = "0".repeat(width - digits.length()) + digits;
        char sign = (negative ? NEG : POS).charAt(digits.charAt(width - 1) - '0');
        return digits.substring(0, width - 1) + sign;
    }

    /** Applies the high-order truncation of a PIC S9(intDigits)V99 item. */
    public static BigDecimal wrap(BigDecimal value, int width, int scale) {
        return decode(encode(value, width, scale), scale);
    }
}
