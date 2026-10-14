package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with EBCDIC-style overpunch sign, as in the sample data. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a zoned field with {@code scale} implied decimals. */
    static BigDecimal decode(String s, int scale) {
        int n = s.length();
        char last = s.charAt(n - 1);
        int p = POS.indexOf(last);
        int q = NEG.indexOf(last);
        int d = p >= 0 ? p : q;
        if (d < 0 && last >= '0' && last <= '9') {
            d = last - '0';
        }
        String digits = s.substring(0, n - 1);
        if (d < 0 || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("Not a zoned decimal: '" + s + "'");
        }
        BigInteger v = new BigInteger(digits + d);
        return new BigDecimal(q >= 0 ? v.negate() : v, scale);
    }

    /** Encodes into exactly {@code width} bytes; high-order digits are dropped (MOVE). Zero is always '{'. */
    static String encode(BigDecimal v, int width) {
        String digits = v.unscaledValue().abs().toString();
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        }
        digits = "0".repeat(width - digits.length()) + digits;
        boolean neg = v.signum() < 0 && !new BigInteger(digits).equals(BigInteger.ZERO);
        char sign = (neg ? NEG : POS).charAt(digits.charAt(width - 1) - '0');
        return digits.substring(0, width - 1) + sign;
    }

    /** Keeps the low {@code intDigits} integer digits, sign kept (COBOL silent truncation). */
    static BigDecimal truncInt(BigDecimal v, int intDigits) {
        BigDecimal mod = BigDecimal.TEN.pow(intDigits);
        return v.remainder(mod);
    }
}
