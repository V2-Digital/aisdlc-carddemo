package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with EBCDIC-style overpunch sign ({=+0 A-I=+1..9 }=-0 J-R=-1..9). */
final class Zoned {
    private Zoned() {
    }

    static BigDecimal decode(String field, int scale) {
        int n = field.length();
        boolean negative = false;
        StringBuilder digits = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = field.charAt(i);
            if (i == n - 1 && !(c >= '0' && c <= '9')) {
                if (c == '{') {
                    c = '0';
                } else if (c >= 'A' && c <= 'I') {
                    c = (char) ('1' + (c - 'A'));
                } else if (c == '}') {
                    c = '0';
                    negative = true;
                } else if (c >= 'J' && c <= 'R') {
                    c = (char) ('1' + (c - 'J'));
                    negative = true;
                }
            }
            if (c < '0' || c > '9') {
                throw new Abend("BAD NUMERIC FIELD: '" + field + "'");
            }
            digits.append(c);
        }
        BigDecimal v = new BigDecimal(new BigInteger(digits.toString()), scale);
        return negative ? v.negate() : v;
    }

    /** Encodes into {@code digits} total digits (incl. {@code scale} decimals), dropping high-order digits. */
    static String encode(BigDecimal value, int digits, int scale) {
        BigInteger unscaled = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean negative = unscaled.signum() < 0;
        String s = unscaled.abs().toString();
        if (s.length() > digits) {
            s = s.substring(s.length() - digits);
        }
        StringBuilder sb = new StringBuilder("0".repeat(digits - s.length())).append(s);
        int last = sb.length() - 1;
        int d = sb.charAt(last) - '0';
        sb.setCharAt(last, negative ? (d == 0 ? '}' : (char) ('J' + d - 1)) : (d == 0 ? '{' : (char) ('A' + d - 1)));
        return sb.toString();
    }

    /** Keeps the low {@code digits} digits of the integer part and the sign, like a COBOL store without SIZE ERROR. */
    static BigDecimal truncate(BigDecimal value, int digits, int scale) {
        BigInteger unscaled = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        BigInteger mod = BigInteger.TEN.pow(digits);
        BigInteger kept = unscaled.abs().mod(mod);
        return new BigDecimal(unscaled.signum() < 0 ? kept.negate() : kept, scale);
    }
}
