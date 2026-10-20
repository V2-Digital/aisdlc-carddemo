package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with trailing overpunch sign ({@code {A-I} = +0..9, {@code }J-R} = -0..9). */
final class Overpunch {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Overpunch() {}

    /** Decode a signed field with two implied decimals. Unsigned digit endings are positive. */
    static BigDecimal parse(String field) {
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean neg = n >= 0;
        int digit = neg ? n : p >= 0 ? p : last - '0';
        BigInteger v = new BigInteger(field.substring(0, field.length() - 1) + digit);
        return new BigDecimal(neg ? v.negate() : v, 2);
    }

    /** Keep only the low {@code digits} digits (COBOL high-order truncation); scale 2. */
    static BigDecimal fit(BigDecimal value, int digits) {
        BigInteger unscaled = value.setScale(2, java.math.RoundingMode.DOWN).unscaledValue();
        BigInteger kept = unscaled.abs().mod(BigInteger.TEN.pow(digits));
        return new BigDecimal(unscaled.signum() < 0 ? kept.negate() : kept, 2);
    }

    /** Encode into {@code width} chars. Zero is always positive ({@code {}). */
    static String format(BigDecimal value, int width) {
        BigInteger kept = fit(value, width).unscaledValue();
        String digits = String.format("%0" + width + "d", kept.abs());
        int last = digits.charAt(width - 1) - '0';
        char sign = kept.signum() < 0 ? NEG.charAt(last) : POS.charAt(last);
        return digits.substring(0, width - 1) + sign;
    }
}
