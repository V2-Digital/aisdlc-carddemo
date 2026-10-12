package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Overpunch zoned decimal S9(n)V99 (-fsign=EBCDIC) and COBOL truncation helpers. */
final class Zoned {
    private Zoned() {}

    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    /** Decodes an S9(n)V99 field to a BigDecimal of scale 2. Throws NumberFormatException on bad data. */
    static BigDecimal decode(String s) {
        int n = s.length();
        if (n < 1) throw new NumberFormatException("empty");
        char last = s.charAt(n - 1);
        int digit;
        boolean neg = false;
        if (last >= '0' && last <= '9') {
            digit = last - '0';
        } else if ((digit = POS.indexOf(last)) >= 0) {
            // positive overpunch
        } else if ((digit = NEG.indexOf(last)) >= 0) {
            neg = true;
        } else {
            throw new NumberFormatException("bad sign byte: " + last);
        }
        for (int i = 0; i < n - 1; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') throw new NumberFormatException("bad digit: " + c);
        }
        BigInteger v = new BigInteger(s.substring(0, n - 1) + digit);
        return new BigDecimal(neg ? v.negate() : v, 2);
    }

    /** Encodes to exactly {@code width} bytes (value must already fit). Zero is always {@code {}. */
    static String encode(BigDecimal v, int width) {
        BigInteger u = v.setScale(2).unscaledValue();
        boolean neg = u.signum() < 0;
        String digits = u.abs().toString();
        StringBuilder sb = new StringBuilder();
        for (int i = digits.length(); i < width; i++) sb.append('0');
        sb.append(digits);
        char last = sb.charAt(width - 1);
        sb.setCharAt(width - 1, (neg ? NEG : POS).charAt(last - '0'));
        return sb.toString();
    }

    /** COBOL truncation toward zero to 2 dp (no ROUNDED). */
    static BigDecimal trunc2(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.DOWN);
    }

    /** Drops high-order digits beyond {@code intDigits} integer digits, keeping the sign (no SIZE ERROR). */
    static BigDecimal wrap(BigDecimal v, int intDigits) {
        BigInteger u = v.setScale(2).unscaledValue();
        BigInteger m = BigInteger.TEN.pow(intDigits + 2);
        BigInteger r = u.abs().mod(m);
        return new BigDecimal(u.signum() < 0 ? r.negate() : r, 2);
    }
}
