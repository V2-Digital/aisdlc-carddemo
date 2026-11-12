package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned decimal (DISPLAY, overpunch sign) with two implied decimals, as used by the CardDemo copybooks. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a zoned field with 2 implied decimals. A non-numeric field is a data exception. */
    static BigDecimal decode(String s) {
        if (s.isEmpty()) {
            throw new Abend("data exception: empty numeric field");
        }
        char last = s.charAt(s.length() - 1);
        boolean neg = false;
        int digit;
        if (last >= '0' && last <= '9') {
            digit = last - '0';
        } else if ((digit = POS.indexOf(last)) >= 0) {
            neg = false;
        } else if ((digit = NEG.indexOf(last)) >= 0) {
            neg = true;
        } else {
            throw new Abend("data exception: " + s);
        }
        String head = s.substring(0, s.length() - 1);
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if (c < '0' || c > '9') {
                throw new Abend("data exception: " + s);
            }
        }
        BigInteger v = new BigInteger(head + digit);
        return new BigDecimal(neg ? v.negate() : v, 2);
    }

    /** Encodes into {@code width} bytes; high-order digits are dropped like COBOL. Zero is always positive. */
    static String encode(BigDecimal value, int width) {
        BigInteger unscaled = value.setScale(2, java.math.RoundingMode.DOWN).unscaledValue();
        BigInteger mag = unscaled.abs().mod(BigInteger.TEN.pow(width));
        boolean neg = unscaled.signum() < 0 && mag.signum() != 0;
        String digits = String.format("%0" + width + "d", mag);
        int d = digits.charAt(width - 1) - '0';
        return digits.substring(0, width - 1) + (neg ? NEG : POS).charAt(d);
    }

    /** Drops high-order digits so the value fits S9(intDigits)V99. */
    static BigDecimal truncate(BigDecimal value, int intDigits) {
        BigInteger u = value.unscaledValue();
        BigInteger mag = u.abs().mod(BigInteger.TEN.pow(intDigits + 2));
        return new BigDecimal(u.signum() < 0 ? mag.negate() : mag, 2);
    }
}
