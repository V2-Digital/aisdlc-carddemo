package carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** EBCDIC-style overpunch zoned decimal codec with COBOL field-width truncation. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decode a zoned field with the given implied decimal scale. */
    static BigDecimal parse(String s, int scale) {
        char last = s.charAt(s.length() - 1);
        boolean neg = false;
        int digit;
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        if (p >= 0) {
            digit = p;
        } else if (n >= 0) {
            digit = n;
            neg = true;
        } else {
            digit = last - '0';
        }
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + digit);
        return new BigDecimal(neg ? v.negate() : v, scale);
    }

    /** Encode into {@code digits} characters (total digits incl. decimals), dropping high-order digits. */
    static String format(BigDecimal value, int scale, int digits) {
        BigInteger u = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean neg = u.signum() < 0;
        String s = u.abs().toString();
        if (s.length() > digits) {
            s = s.substring(s.length() - digits);
        }
        s = "0".repeat(digits - s.length()) + s;
        char last = (neg ? NEG : POS).charAt(s.charAt(digits - 1) - '0');
        return s.substring(0, digits - 1) + last;
    }

    /** Truncate to {@code intDigits} integer digits at the given scale, keeping the sign. */
    static BigDecimal truncate(BigDecimal value, int scale, int intDigits) {
        BigInteger mod = BigInteger.TEN.pow(intDigits + scale);
        return new BigDecimal(value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue().remainder(mod), scale);
    }
}
