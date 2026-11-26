package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal S9(n)V99 with EBCDIC-style overpunch signs, as in the sample data. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    static BigDecimal decode(String s) {
        char last = s.charAt(s.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        int digit;
        boolean negative = false;
        if (p >= 0) {
            digit = p;
        } else if (n >= 0) {
            digit = n;
            negative = true;
        } else if (last >= '0' && last <= '9') {
            digit = last - '0';
        } else {
            throw new IllegalArgumentException("Bad zoned decimal: '" + s + "'");
        }
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + digit);
        return new BigDecimal(negative ? v.negate() : v, 2);
    }

    /** Encodes into {@code len} bytes, dropping high-order digits like COBOL (no SIZE ERROR). */
    static String encode(BigDecimal value, int len) {
        BigInteger unscaled = value.setScale(2, java.math.RoundingMode.UNNECESSARY).unscaledValue();
        boolean negative = unscaled.signum() < 0;
        String digits = unscaled.abs().toString();
        if (digits.length() > len) {
            digits = digits.substring(digits.length() - len);
        }
        digits = "0".repeat(len - digits.length()) + digits;
        char sign = (negative ? NEG : POS).charAt(digits.charAt(len - 1) - '0');
        return digits.substring(0, len - 1) + sign;
    }

    /** Keeps the low-order {@code len} digits (sign kept), i.e. the COBOL truncation. */
    static BigDecimal wrap(BigDecimal value, int len) {
        BigInteger mod = BigInteger.TEN.pow(len);
        return new BigDecimal(value.unscaledValue().remainder(mod), 2);
    }
}
