package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** EBCDIC-style overpunch zoned decimals (implied 2 decimals) with COBOL high-order truncation. */
public final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a signed zoned field with two implied decimals. */
    public static BigDecimal decode(String s) {
        char last = s.charAt(s.length() - 1);
        boolean neg = NEG.indexOf(last) >= 0;
        int digit = neg ? NEG.indexOf(last) : POS.indexOf(last) >= 0 ? POS.indexOf(last) : last - '0';
        String digits = s.substring(0, s.length() - 1) + digit;
        BigDecimal v = new BigDecimal(new BigInteger(digits), 2);
        return neg ? v.negate() : v;
    }

    /** Truncates toward zero at 2 decimals and drops high-order digits beyond intDigits. */
    public static BigDecimal truncate(BigDecimal v, int intDigits) {
        BigDecimal t = v.setScale(2, RoundingMode.DOWN);
        BigInteger unscaled = t.unscaledValue().abs().mod(BigInteger.TEN.pow(intDigits + 2));
        return new BigDecimal(t.signum() < 0 ? unscaled.negate() : unscaled, 2);
    }

    /** Encodes into intDigits + 2 characters; zero is always positive. */
    public static String encode(BigDecimal v, int intDigits) {
        BigDecimal t = truncate(v, intDigits);
        String digits = String.format("%0" + (intDigits + 2) + "d", t.unscaledValue().abs());
        int last = digits.charAt(digits.length() - 1) - '0';
        char sign = t.signum() < 0 ? NEG.charAt(last) : POS.charAt(last);
        return digits.substring(0, digits.length() - 1) + sign;
    }
}
