package carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal with EBCDIC-style overpunch sign, implied scale 2 (PIC S9(n)V99). */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    static BigDecimal parse(String s) {
        char last = s.charAt(s.length() - 1);
        int p = POS.indexOf(last);
        boolean neg = false;
        if (p < 0) {
            p = NEG.indexOf(last);
            neg = true;
        }
        if (p < 0) {
            throw new IllegalArgumentException("Bad zoned decimal: '" + s + "'");
        }
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + p);
        return new BigDecimal(neg ? v.negate() : v, 2);
    }

    /** Formats to {@code digits} characters; high-order digits are lost, as in COBOL. -0 is written as +0. */
    static String format(BigDecimal value, int digits) {
        BigInteger u = value.setScale(2, java.math.RoundingMode.DOWN).unscaledValue();
        boolean neg = u.signum() < 0;
        BigInteger a = u.abs().mod(BigInteger.TEN.pow(digits));
        String d = String.format("%0" + digits + "d", a);
        int last = d.charAt(digits - 1) - '0';
        boolean negative = neg && a.signum() != 0;
        return d.substring(0, digits - 1) + (negative ? NEG : POS).charAt(last);
    }

    /** Truncates a value to the range of S9(n)V99, keeping the sign. */
    static BigDecimal fit(BigDecimal value, int digits) {
        return parse(format(value, digits));
    }
}
