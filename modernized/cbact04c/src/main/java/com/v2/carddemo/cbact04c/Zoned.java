package com.v2.carddemo.cbact04c;

/** Zoned-decimal (DISPLAY, trailing overpunch sign) helpers. Values are scaled integers (implied decimal point). */
public final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a zoned field such as {@code 0000000187G} into its scaled integer value. */
    public static long decode(String field) {
        int n = field.length();
        char last = field.charAt(n - 1);
        int p = POS.indexOf(last);
        boolean neg = false;
        int d;
        if (p >= 0) {
            d = p;
        } else if ((p = NEG.indexOf(last)) >= 0) {
            d = p;
            neg = true;
        } else if (last >= '0' && last <= '9') {
            d = last - '0';
        } else {
            throw new IllegalArgumentException("Bad zoned field: " + field);
        }
        long v = 0;
        for (int i = 0; i < n - 1; i++) {
            v = v * 10 + (field.charAt(i) - '0');
        }
        v = v * 10 + d;
        return neg ? -v : v;
    }

    /** Encodes into {@code digits} digits; high-order digits beyond the width are silently dropped (no SIZE ERROR). */
    public static String encode(long value, int digits) {
        long v = truncate(value, digits);
        boolean neg = v < 0;
        String s = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < digits; i++) {
            sb.append('0');
        }
        sb.append(s);
        int last = sb.charAt(digits - 1) - '0';
        sb.setCharAt(digits - 1, (neg ? NEG : POS).charAt(last));
        return sb.toString();
    }

    /** Keeps the low {@code digits} digits of the magnitude, sign preserved. */
    public static long truncate(long value, int digits) {
        long mod = 1;
        for (int i = 0; i < digits; i++) {
            mod *= 10;
        }
        return value % mod;
    }
}
