package com.v2.carddemo.cbtrn02c;

/** Zoned decimal with EBCDIC-style overpunch sign (GnuCOBOL -fsign=EBCDIC). Values are held as cents. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decode S9(n)V99 text to a signed long of cents. -0 decodes to 0. */
    static long decode(String s) {
        int n = s.length();
        char last = s.charAt(n - 1);
        int p = POS.indexOf(last);
        int q = NEG.indexOf(last);
        boolean neg = q >= 0;
        int d = neg ? q : p;
        if (d < 0) {
            // plain digit: unsigned / positive
            d = last - '0';
            if (d < 0 || d > 9) throw new NumberFormatException("bad zoned: " + s);
        }
        long v = 0;
        for (int i = 0; i < n - 1; i++) {
            int c = s.charAt(i) - '0';
            if (c < 0 || c > 9) throw new NumberFormatException("bad zoned: " + s);
            v = v * 10 + c;
        }
        v = v * 10 + d;
        return neg ? -v : v;
    }

    /** Encode to exactly {@code digits} characters, truncating on the left (no SIZE ERROR). */
    static String encode(long value, int digits) {
        long mod = 1;
        for (int i = 0; i < digits; i++) mod *= 10;
        long a = Math.abs(value) % mod;
        String t = Long.toString(a);
        StringBuilder sb = new StringBuilder();
        for (int i = t.length(); i < digits; i++) sb.append('0');
        sb.append(t);
        int last = sb.length() - 1;
        int d = sb.charAt(last) - '0';
        sb.setCharAt(last, (value < 0 ? NEG : POS).charAt(d));
        return sb.toString();
    }

    /** Value as stored after a COBOL arithmetic store into a field of {@code digits} digits. */
    static long truncate(long value, int digits) {
        long mod = 1;
        for (int i = 0; i < digits; i++) mod *= 10;
        long r = Math.abs(value) % mod;
        return value < 0 ? -r : r;
    }
}
