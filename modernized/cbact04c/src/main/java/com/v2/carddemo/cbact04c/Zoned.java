package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned-decimal (DISPLAY, EBCDIC-style overpunch) codec for signed fields with 2 implied decimals. */
final class Zoned {
    private Zoned() {}

    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    /** Decodes {@code s} (digits, last byte carries the sign) as a value with scale 2. */
    static BigDecimal decode(String s) {
        char last = s.charAt(s.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        int digit;
        boolean negative = n >= 0;
        if (p >= 0) {
            digit = p;
        } else if (n >= 0) {
            digit = n;
        } else if (last >= '0' && last <= '9') {
            digit = last - '0';
        } else {
            throw new IllegalArgumentException("Bad zoned decimal: " + s);
        }
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + digit);
        return new BigDecimal(negative ? v.negate() : v, 2);
    }

    /** Encodes {@code v} into {@code width} bytes, truncating high-order digits as COBOL does. Zero is always +0. */
    static String encode(BigDecimal v, int width) {
        BigInteger u = v.setScale(2, java.math.RoundingMode.DOWN).unscaledValue();
        boolean negative = u.signum() < 0;
        String digits = u.abs().mod(BigInteger.TEN.pow(width)).toString();
        digits = "0".repeat(width - digits.length()) + digits;
        if (negative && digits.chars().allMatch(c -> c == '0')) {
            negative = false;
        }
        int d = digits.charAt(width - 1) - '0';
        return digits.substring(0, width - 1) + (negative ? NEG : POS).charAt(d);
    }
}
