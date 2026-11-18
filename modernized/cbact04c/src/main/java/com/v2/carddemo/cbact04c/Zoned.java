package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** COBOL zoned decimal (DISPLAY, SIGN TRAILING overpunch) codec. */
public final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decode digits with an overpunched last char; {@code scale} implied decimals. */
    public static BigDecimal decode(String s, int scale) {
        int n = s.length();
        char last = s.charAt(n - 1);
        int digit;
        boolean neg = false;
        if (last >= '0' && last <= '9') {
            digit = last - '0';
        } else if ((digit = POS.indexOf(last)) >= 0) {
            // positive
        } else if ((digit = NEG.indexOf(last)) >= 0) {
            neg = true;
        } else {
            throw new IllegalArgumentException("Invalid zoned sign char '" + last + "' in '" + s + "'");
        }
        String head = s.substring(0, n - 1);
        for (int i = 0; i < head.length(); i++) {
            if (head.charAt(i) < '0' || head.charAt(i) > '9') {
                throw new IllegalArgumentException("Invalid zoned digit in '" + s + "'");
            }
        }
        BigInteger u = new BigInteger(head + digit);
        return new BigDecimal(neg ? u.negate() : u, scale);
    }

    /** Encode to {@code width} chars; excess high-order digits are dropped. Zero is positive. */
    public static String encode(BigDecimal v, int width, int scale) {
        BigInteger u = v.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        boolean neg = u.signum() < 0;
        String d = u.abs().toString();
        if (d.length() > width) {
            d = d.substring(d.length() - width);
        }
        d = "0".repeat(width - d.length()) + d;
        int last = d.charAt(width - 1) - '0';
        boolean negative = neg && !new BigInteger(d).equals(BigInteger.ZERO);
        char sign = (negative ? NEG : POS).charAt(last);
        return d.substring(0, width - 1) + sign;
    }

    /** COBOL high-order truncation to {@code intDigits} integer digits (no SIZE ERROR), sign kept. */
    public static BigDecimal truncate(BigDecimal v, int intDigits, int scale) {
        BigInteger u = v.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        BigInteger mod = BigInteger.TEN.pow(intDigits + scale);
        BigInteger r = u.abs().mod(mod);
        return new BigDecimal(u.signum() < 0 ? r.negate() : r, scale);
    }
}
