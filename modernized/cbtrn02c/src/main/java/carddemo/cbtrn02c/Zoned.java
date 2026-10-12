package carddemo.cbtrn02c;

/** Zoned decimal with EBCDIC-style overpunch sign, as signed cents (implied 2 decimals). */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decode a field; a plain last digit is positive. */
    static long decode(String s) {
        int last = s.length() - 1;
        char c = s.charAt(last);
        boolean neg = false;
        int d;
        if (c >= '0' && c <= '9') {
            d = c - '0';
        } else if (POS.indexOf(c) >= 0) {
            d = POS.indexOf(c);
        } else if (NEG.indexOf(c) >= 0) {
            d = NEG.indexOf(c);
            neg = true;
        } else {
            throw new LoadException("bad zoned decimal: '" + s + "'");
        }
        long v = 0;
        for (int i = 0; i < last; i++) {
            char x = s.charAt(i);
            if (x < '0' || x > '9') {
                throw new LoadException("bad zoned decimal: '" + s + "'");
            }
            v = v * 10 + (x - '0');
        }
        v = v * 10 + d;
        return neg ? -v : v;
    }

    /** Truncate to {@code digits} total digits (high-order loss, no error), as COBOL without SIZE ERROR. */
    static long trunc(long v, int digits) {
        long mod = 1;
        for (int i = 0; i < digits; i++) {
            mod *= 10;
        }
        long a = Math.abs(v) % mod;
        return v < 0 ? -a : a;
    }

    /** Encode an arithmetic result into {@code digits} characters. Zero is always '{'. */
    static String encode(long v, int digits) {
        long t = trunc(v, digits);
        boolean neg = t < 0;
        long a = Math.abs(t);
        char[] out = new char[digits];
        for (int i = digits - 1; i >= 0; i--) {
            out[i] = (char) ('0' + a % 10);
            a /= 10;
        }
        int d = out[digits - 1] - '0';
        out[digits - 1] = neg ? NEG.charAt(d) : POS.charAt(d);
        return new String(out);
    }
}
