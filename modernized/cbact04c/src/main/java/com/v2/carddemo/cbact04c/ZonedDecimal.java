package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Signed zoned decimal with EBCDIC-style overpunch ({=+0, A-I=+1..9, }=-0, J-R=-1..9). */
public final class ZonedDecimal {
    private static final String POSITIVE = "{ABCDEFGHI";
    private static final String NEGATIVE = "}JKLMNOPQR";

    private ZonedDecimal() {
    }

    /** Parses a PIC S9(n)V9(scale) field; throws AbendException when not numeric. */
    public static BigDecimal parse(String field, int scale) {
        if (field.isEmpty()) {
            throw new AbendException("NON-NUMERIC FIELD");
        }
        char last = field.charAt(field.length() - 1);
        int sign = 1;
        int digit = last - '0';
        int pos = POSITIVE.indexOf(last);
        int neg = NEGATIVE.indexOf(last);
        if (pos >= 0) {
            digit = pos;
        } else if (neg >= 0) {
            digit = neg;
            sign = -1;
        } else if (digit < 0 || digit > 9) {
            throw new AbendException("NON-NUMERIC FIELD");
        }
        StringBuilder digits = new StringBuilder(field.substring(0, field.length() - 1));
        digits.append((char) ('0' + digit));
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new AbendException("NON-NUMERIC FIELD");
            }
        }
        BigInteger unscaled = new BigInteger(digits.toString());
        return new BigDecimal(sign < 0 ? unscaled.negate() : unscaled, scale);
    }

    /** Keeps only the low-order {@code intDigits} integer digits (COBOL store without ON SIZE ERROR). */
    public static BigDecimal truncateHighOrder(BigDecimal value, int intDigits) {
        BigInteger mod = BigInteger.TEN.pow(intDigits + value.scale());
        return new BigDecimal(value.unscaledValue().remainder(mod), value.scale());
    }

    /** Formats as PIC S9(intDigits)V9(scale): fixed width, overpunch sign, -0 written as +0. */
    public static String format(BigDecimal value, int intDigits, int scale) {
        BigDecimal v = truncateHighOrder(value.setScale(scale, java.math.RoundingMode.DOWN), intDigits);
        BigInteger unscaled = v.unscaledValue();
        boolean negative = unscaled.signum() < 0;
        String digits = unscaled.abs().toString();
        int width = intDigits + scale;
        StringBuilder sb = new StringBuilder();
        for (int i = digits.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(digits);
        int last = sb.charAt(width - 1) - '0';
        sb.setCharAt(width - 1, (negative ? NEGATIVE : POSITIVE).charAt(last));
        return sb.toString();
    }
}
