package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** EBCDIC-style overpunch zoned decimals with 2 implied decimals ({@code PIC S9(n)V99}). */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a signed zoned field with scale 2. */
    static BigDecimal decode(String field) {
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        int digit = p >= 0 ? p : n;
        if (digit < 0) {
            throw new IllegalArgumentException("Bad zoned sign: " + field);
        }
        BigInteger v = new BigInteger(field.substring(0, field.length() - 1) + digit);
        return new BigDecimal(n >= 0 ? v.negate() : v, 2);
    }

    /** Encodes into {@code len} bytes, keeping the low-order digits only (COBOL truncation). */
    static String encode(BigDecimal value, int len) {
        BigInteger u = value.movePointRight(2).toBigInteger().remainder(BigInteger.TEN.pow(len));
        String digits = String.format("%0" + len + "d", u.abs());
        int d = digits.charAt(len - 1) - '0';
        char sign = u.signum() < 0 ? NEG.charAt(d) : POS.charAt(d);
        return digits.substring(0, len - 1) + sign;
    }

    /** Value kept modulo 10^digits (sign preserved), scale 2. */
    static BigDecimal fit(BigDecimal value, int digits) {
        return new BigDecimal(value.movePointRight(2).toBigInteger().remainder(BigInteger.TEN.pow(digits)), 2);
    }
}
