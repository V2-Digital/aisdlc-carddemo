package carddemo.cbtrn02c;

/** Zoned decimal with trailing overpunch sign (EBCDIC-style, as in the sample data) <-> long (scaled value). */
public final class Overpunch {
    private Overpunch() {}

    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    /** Decodes {@code len} bytes at 0-based {@code off}; the scale (V99) is the caller's concern. */
    public static long decode(byte[] rec, int off, int len) {
        long v = 0;
        for (int i = 0; i < len - 1; i++) {
            v = v * 10 + (rec[off + i] - '0');
        }
        char last = (char) (rec[off + len - 1] & 0xFF);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        if (n >= 0) {
            return -(v * 10 + n);
        }
        if (p >= 0) {
            return v * 10 + p;
        }
        return v * 10 + (last - '0');
    }

    /** Encodes into {@code len} bytes; zero and positive use {@code {A-I}, negative uses {@code }J-R}. */
    public static void encode(long value, byte[] rec, int off, int len) {
        long a = Math.abs(value);
        int last = (int) (a % 10);
        a /= 10;
        for (int i = len - 2; i >= 0; i--) {
            rec[off + i] = (byte) ('0' + a % 10);
            a /= 10;
        }
        rec[off + len - 1] = (byte) (value < 0 ? NEG.charAt(last) : POS.charAt(last));
    }

    /** COBOL store without ON SIZE ERROR: keep the sign and the low {@code digits} digits. */
    public static long truncate(long value, int digits) {
        long mod = 1;
        for (int i = 0; i < digits; i++) {
            mod *= 10;
        }
        long a = Math.abs(value) % mod;
        return value < 0 ? -a : a;
    }
}
