package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with EBCDIC-style overpunch sign in the last byte (GnuCOBOL -fsign=EBCDIC). */
final class Zoned {
    private Zoned() {}

    static BigDecimal decode(String s, int scale) {
        char last = s.charAt(s.length() - 1);
        boolean neg = false;
        int d;
        if (last == '{') d = 0;
        else if (last == '}') { d = 0; neg = true; }
        else if (last >= 'A' && last <= 'I') d = last - 'A' + 1;
        else if (last >= 'J' && last <= 'R') { d = last - 'J' + 1; neg = true; }
        else d = last - '0';
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + d);
        return new BigDecimal(neg ? v.negate() : v, scale);
    }

    /** Keeps the low-order {@code digits} digits (COBOL truncation), sign kept. */
    static BigDecimal truncate(BigDecimal v, int digits) {
        BigInteger u = v.unscaledValue();
        BigInteger m = BigInteger.TEN.pow(digits);
        return new BigDecimal(u.remainder(m), v.scale()); // remainder keeps the sign of u
    }

    static String encode(BigDecimal v, int width) {
        BigInteger u = v.unscaledValue();
        boolean neg = u.signum() < 0;
        String digits = u.abs().toString();
        digits = "0".repeat(Math.max(0, width - digits.length())) + digits;
        digits = digits.substring(digits.length() - width);
        int d = digits.charAt(width - 1) - '0';
        char c = neg ? (d == 0 ? '}' : (char) ('J' + d - 1)) : (d == 0 ? '{' : (char) ('A' + d - 1));
        return digits.substring(0, width - 1) + c;
    }
}
