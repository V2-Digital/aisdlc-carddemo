package carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned DISPLAY decimal with trailing EBCDIC-style overpunch sign ({=+0, A-I=+1..9, }=-0, J-R=-1..9). */
final class Zoned {
    private Zoned() {}

    static BigDecimal decode(String s, int scale) {
        char last = s.charAt(s.length() - 1);
        boolean neg = false;
        char digit = last;
        if (last == '{') {
            digit = '0';
        } else if (last >= 'A' && last <= 'I') {
            digit = (char) ('1' + (last - 'A'));
        } else if (last == '}') {
            digit = '0';
            neg = true;
        } else if (last >= 'J' && last <= 'R') {
            digit = (char) ('1' + (last - 'J'));
            neg = true;
        }
        BigInteger v = new BigInteger(s.substring(0, s.length() - 1) + digit);
        return new BigDecimal(neg ? v.negate() : v, scale);
    }

    /** Keeps the low {@code width} digits of |value| (COBOL high-order truncation); zero is always '{'. */
    static String encode(BigDecimal value, int scale, int width) {
        BigInteger unscaled = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean neg = unscaled.signum() < 0;
        BigInteger mod = unscaled.abs().mod(BigInteger.TEN.pow(width));
        String digits = String.format("%0" + width + "d", mod);
        if (mod.signum() == 0) {
            neg = false;
        }
        int d = digits.charAt(width - 1) - '0';
        char sign = neg ? (d == 0 ? '}' : (char) ('J' + d - 1)) : (d == 0 ? '{' : (char) ('A' + d - 1));
        return digits.substring(0, width - 1) + sign;
    }

    /** Truncates a signed value to {@code width} digits at {@code scale}, keeping the sign. */
    static BigDecimal truncate(BigDecimal value, int scale, int width) {
        BigInteger u = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        BigInteger m = u.abs().mod(BigInteger.TEN.pow(width));
        return new BigDecimal(u.signum() < 0 ? m.negate() : m, scale);
    }
}
