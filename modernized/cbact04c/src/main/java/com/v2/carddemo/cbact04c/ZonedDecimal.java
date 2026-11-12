package com.v2.carddemo.cbact04c;

/** EBCDIC-style overpunch zoned decimal ({@code S9(n)V99}) as signed cents. */
final class ZonedDecimal {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private ZonedDecimal() {}

    /** Decodes a signed zoned field (last byte overpunched) to its integer value (cents). */
    static long decode(String field) {
        int n = field.length();
        char last = field.charAt(n - 1);
        int p = POS.indexOf(last);
        int q = NEG.indexOf(last);
        if (p < 0 && q < 0) {
            throw new IllegalArgumentException("bad overpunch '" + last + "' in " + field);
        }
        long v = Long.parseLong(field.substring(0, n - 1) + (p >= 0 ? p : q));
        return p >= 0 ? v : -v;
    }

    /** Encodes to {@code width} bytes; +0 is '{', negative (incl. {@code negZero}) uses '}' / 'J'-'R'. */
    static String encode(long value, int width, boolean negZero) {
        boolean neg = value < 0 || (value == 0 && negZero);
        String digits = Long.toString(Math.abs(value));
        if (digits.length() > width) {
            throw new IllegalArgumentException(value + " does not fit " + width);
        }
        digits = "0".repeat(width - digits.length()) + digits;
        int d = digits.charAt(width - 1) - '0';
        return digits.substring(0, width - 1) + (neg ? NEG : POS).charAt(d);
    }

    static String encode(long value, int width) {
        return encode(value, width, false);
    }

    /** Silent high-order truncation to {@code digits} digits, keeping the sign (no ON SIZE ERROR). */
    static long wrap(long value, int digits) {
        long mod = 1;
        for (int i = 0; i < digits; i++) {
            mod *= 10;
        }
        return value % mod;
    }
}
