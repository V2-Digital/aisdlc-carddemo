package com.v2.carddemo.cbact04c;

/** Zoned decimal with EBCDIC-style overpunch sign in the last byte; values are scaled longs (cents). */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    static long decode(String field) {
        int n = field.length();
        long v = 0;
        for (int i = 0; i < n - 1; i++) {
            v = v * 10 + (field.charAt(i) - '0');
        }
        char last = field.charAt(n - 1);
        int p = POS.indexOf(last);
        int q = NEG.indexOf(last);
        if (p >= 0) {
            return v * 10 + p;
        }
        if (q >= 0) {
            return -(v * 10 + q);
        }
        return v * 10 + (last - '0');
    }

    /** Encodes into exactly {@code width} bytes, keeping the low-order digits (COBOL MOVE truncation). */
    static String encode(long value, int width) {
        long abs = Math.abs(value);
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            sb.append((char) ('0' + abs % 10));
            abs /= 10;
        }
        sb.reverse();
        int last = sb.charAt(width - 1) - '0';
        sb.setCharAt(width - 1, (value < 0 ? NEG : POS).charAt(last));
        return sb.toString();
    }

    /** Wraps to the given number of digits, keeping the sign (high-order truncation). */
    static long wrap(long value, int digits) {
        long mod = 1;
        for (int i = 0; i < digits; i++) {
            mod *= 10;
        }
        return value % mod;
    }
}
