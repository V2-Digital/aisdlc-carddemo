package carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Overpunch zoned decimal S9(n)V99: {=+0, A-I=+1..9, }=-0, J-R=-1..9. */
final class Zoned {
    private Zoned() {}

    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    /** Decodes a zoned field with two implied decimals. */
    static BigDecimal decode(String s) {
        char last = s.charAt(s.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        boolean neg = n >= 0;
        int d = neg ? n : p;
        if (d < 0) {
            throw new IllegalArgumentException("bad zoned value: " + s);
        }
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + d);
        return new BigDecimal(neg ? v.negate() : v, 2);
    }

    /** Drops high-order digits beyond intDigits (COBOL silent size truncation), keeping the sign. */
    static BigDecimal wrap(BigDecimal v, int intDigits) {
        BigInteger mod = BigInteger.TEN.pow(intDigits + 2);
        BigInteger u = v.setScale(2, java.math.RoundingMode.DOWN).unscaledValue();
        BigInteger r = u.abs().mod(mod);
        return new BigDecimal(u.signum() < 0 ? r.negate() : r, 2);
    }

    /** Encodes into a zoned field of intDigits + 2 characters. */
    static String encode(BigDecimal v, int intDigits) {
        BigDecimal w = wrap(v, intDigits);
        String digits = w.unscaledValue().abs().toString();
        int len = intDigits + 2;
        digits = "0".repeat(len - digits.length()) + digits;
        int d = digits.charAt(len - 1) - '0';
        char sign = (w.signum() < 0 ? NEG : POS).charAt(d);
        return digits.substring(0, len - 1) + sign;
    }
}
