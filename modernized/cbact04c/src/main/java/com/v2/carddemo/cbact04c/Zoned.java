package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Zoned decimal with trailing overpunch sign ({ A-I = +0..9, } J-R = -0..9), two implied decimals. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    static BigDecimal decode(String s) {
        int n = s.length();
        char last = s.charAt(n - 1);
        int d = POS.indexOf(last);
        boolean neg = false;
        if (d < 0) {
            d = NEG.indexOf(last);
            neg = true;
        }
        if (d < 0) {
            throw new IllegalArgumentException("bad zoned data: '" + s + "'");
        }
        String digits = s.substring(0, n - 1);
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("bad zoned data: '" + s + "'");
            }
        }
        BigInteger cents = new BigInteger(digits + d);
        return new BigDecimal(neg ? cents.negate() : cents, 2);
    }

    /** MOVE/COMPUTE into S9(intDigits)V99 without SIZE ERROR: truncate to 2 dp, drop high-order digits. */
    static BigDecimal store(BigDecimal v, int intDigits) {
        BigInteger cents = v.setScale(2, RoundingMode.DOWN).unscaledValue();
        BigInteger mod = BigInteger.TEN.pow(intDigits + 2);
        return new BigDecimal(cents.remainder(mod), 2);
    }

    /** Encodes into intDigits+2 characters. Zero is always written as positive. */
    static String encode(BigDecimal v, int intDigits) {
        BigInteger cents = store(v, intDigits).unscaledValue();
        boolean neg = cents.signum() < 0;
        String digits = cents.abs().toString();
        int width = intDigits + 2;
        digits = "0".repeat(width - digits.length()) + digits;
        char last = (neg ? NEG : POS).charAt(digits.charAt(width - 1) - '0');
        return digits.substring(0, width - 1) + last;
    }
}
