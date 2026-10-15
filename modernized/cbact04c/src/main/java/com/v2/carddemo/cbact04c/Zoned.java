package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** EBCDIC-style zoned decimal (overpunch sign in the last byte) with COBOL truncation semantics. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decode a signed zoned field with the given number of implied decimals. */
    static BigDecimal decode(String field, int scale) {
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean negative = n >= 0;
        char digit = (char) ('0' + (negative ? n : p >= 0 ? p : last - '0'));
        String digits = field.substring(0, field.length() - 1) + digit;
        BigInteger v = new BigInteger(digits.trim().isEmpty() ? "0" : digits.trim());
        return new BigDecimal(negative ? v.negate() : v, scale);
    }

    /** Keep only the low {@code digits} digits (incl. decimals) of the unscaled value, sign kept. */
    static BigDecimal wrap(BigDecimal v, int digits) {
        BigInteger m = BigInteger.TEN.pow(digits);
        return new BigDecimal(v.unscaledValue().remainder(m), v.scale());
    }

    /** Encode as a signed zoned field of {@code width} bytes; scale is taken as 2 decimals. */
    static String encode(BigDecimal v, int width) {
        BigInteger u = wrap(v, width).unscaledValue();
        String digits = u.abs().toString();
        digits = "0".repeat(width - digits.length()) + digits;
        int d = digits.charAt(width - 1) - '0';
        char sign = (u.signum() < 0 ? NEG : POS).charAt(d);
        return digits.substring(0, width - 1) + sign;
    }
}
