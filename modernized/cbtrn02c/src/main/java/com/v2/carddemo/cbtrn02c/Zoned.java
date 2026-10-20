package com.v2.carddemo.cbtrn02c;

import java.math.BigInteger;

/** Zoned decimal (DISPLAY, EBCDIC-style overpunch sign) codec. Values are held as scaled longs (cents). */
final class Zoned {
    private Zoned() {}

    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    /** Decode a zoned field; a plain trailing digit is positive. */
    static long decode(String field) {
        int n = field.length();
        char last = field.charAt(n - 1);
        boolean neg = false;
        int d = POS.indexOf(last);
        if (d < 0) {
            d = NEG.indexOf(last);
            neg = d >= 0;
        }
        if (d < 0) {
            if (last < '0' || last > '9') {
                throw new IllegalArgumentException("bad sign byte '" + last + "'");
            }
            d = last - '0';
        }
        long v = 0;
        for (int i = 0; i < n - 1; i++) {
            char c = field.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("bad digit '" + c + "'");
            }
            v = v * 10 + (c - '0');
        }
        v = v * 10 + d;
        return neg ? -v : v;
    }

    /** Keep the sign, drop high-order digits beyond {@code digits} (COBOL store without SIZE ERROR). */
    static long truncate(long v, int digits) {
        long mod = BigInteger.TEN.pow(digits).longValueExact();
        return v % mod;
    }

    /** Encode into exactly {@code digits} characters; zero is always positive ({@code {}). */
    static String encode(long v, int digits) {
        long t = truncate(v, digits);
        boolean neg = t < 0;
        String s = Long.toString(Math.abs(t));
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < digits; i++) {
            sb.append('0');
        }
        sb.append(s);
        char last = sb.charAt(digits - 1);
        sb.setCharAt(digits - 1, (neg ? NEG : POS).charAt(last - '0'));
        return sb.toString();
    }
}
