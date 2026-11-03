package com.v2.carddemo.cbact04c;

/** Zoned-decimal (EBCDIC overpunch) codec. Values are held as scaled longs (cents, hundredths). */
public final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decode a signed zoned field: the last character carries digit and sign. */
    public static long decode(String s) {
        int n = s.length();
        char last = s.charAt(n - 1);
        int p = POS.indexOf(last);
        int q = NEG.indexOf(last);
        long v = n > 1 ? Long.parseLong(s.substring(0, n - 1)) : 0;
        int d;
        boolean neg = false;
        if (p >= 0) {
            d = p;
        } else if (q >= 0) {
            d = q;
            neg = true;
        } else {
            d = last - '0';
            if (d < 0 || d > 9) throw new IllegalArgumentException("bad zoned field: " + s);
        }
        v = v * 10 + d;
        return neg ? -v : v;
    }

    /** Encode into {@code digits} characters, dropping high-order digits like COBOL. Zero is positive. */
    public static String encode(long value, int digits) {
        long mod = pow10(digits);
        long a = Math.abs(value) % mod;
        boolean neg = value < 0 && a != 0;
        String d = String.format("%0" + digits + "d", a);
        int last = d.charAt(digits - 1) - '0';
        return d.substring(0, digits - 1) + (neg ? NEG : POS).charAt(last);
    }

    /** Unsigned PIC 9(n). */
    public static String digits(long value, int digits) {
        return String.format("%0" + digits + "d", Math.abs(value) % pow10(digits));
    }

    /** Silent high-order truncation to {@code digits} digits, keeping the sign (no SIZE ERROR clause). */
    public static long truncate(long value, int digits) {
        return value % pow10(digits);
    }

    static long pow10(int n) {
        long r = 1;
        for (int i = 0; i < n; i++) r *= 10;
        return r;
    }
}
