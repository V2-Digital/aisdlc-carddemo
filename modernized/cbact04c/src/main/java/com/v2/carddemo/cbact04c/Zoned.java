package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned-decimal (DISPLAY, trailing overpunch sign) codec, scale 2. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a field with an implied 2-digit fraction, e.g. S9(09)V99. */
    static BigDecimal decode(String s) {
        char last = s.charAt(s.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        int digit = p >= 0 ? p : n;
        if (digit < 0) {
            throw new IllegalArgumentException("bad sign char '" + last + "'");
        }
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + digit);
        if (n >= 0) {
            v = v.negate();
        }
        return new BigDecimal(v, 2);
    }

    /** Encodes into {@code width} chars, dropping high-order digits like COBOL; zero is always '{'. */
    static String encode(BigDecimal value, int width) {
        BigInteger u = value.setScale(2, java.math.RoundingMode.DOWN).unscaledValue();
        boolean neg = u.signum() < 0;
        BigInteger mag = u.abs().mod(BigInteger.TEN.pow(width));
        if (mag.signum() == 0) {
            neg = false;
        }
        String digits = String.format("%0" + width + "d", mag);
        int d = digits.charAt(width - 1) - '0';
        return digits.substring(0, width - 1) + (neg ? NEG : POS).charAt(d);
    }

    /** Keeps the low-order {@code intDigits} integer digits (sign kept), as an unchecked COBOL store does. */
    static BigDecimal wrap(BigDecimal v, int intDigits) {
        BigInteger u = v.unscaledValue();
        BigInteger mag = u.abs().mod(BigInteger.TEN.pow(intDigits + 2));
        return new BigDecimal(u.signum() < 0 ? mag.negate() : mag, 2);
    }
}
