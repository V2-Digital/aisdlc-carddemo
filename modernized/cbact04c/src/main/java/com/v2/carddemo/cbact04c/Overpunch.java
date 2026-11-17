package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with trailing overpunch sign: {A-I = +0..9, }J-R = -0..9. */
public final class Overpunch {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Overpunch() {}

    /** Decodes {@code digits} (last char carries the sign) into a value with the given scale. */
    public static BigDecimal decode(String field, int scale) {
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean negative = n >= 0;
        int lastDigit = negative ? n : p >= 0 ? p : Character.digit(last, 10);
        if (lastDigit < 0) {
            throw new IllegalArgumentException("Bad zoned decimal: '" + field + "'");
        }
        String digits = field.substring(0, field.length() - 1) + lastDigit;
        BigInteger v = new BigInteger(digits.isBlank() ? "0" : digits.strip());
        return new BigDecimal(negative ? v.negate() : v, scale);
    }

    /** Encodes {@code value} (scale already fixed by the caller) into {@code width} digits. */
    public static String encode(BigDecimal value, int scale, int width) {
        BigInteger u = value.setScale(scale).unscaledValue();
        boolean negative = u.signum() < 0;
        String digits = u.abs().toString();
        digits = "0".repeat(Math.max(0, width - digits.length())) + digits;
        digits = digits.substring(digits.length() - width);
        int d = digits.charAt(width - 1) - '0';
        return digits.substring(0, width - 1) + (negative ? NEG : POS).charAt(d);
    }
}
