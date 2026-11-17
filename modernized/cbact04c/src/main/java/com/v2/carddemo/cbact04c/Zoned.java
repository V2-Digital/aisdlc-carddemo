package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Zoned DISPLAY decimals with trailing overpunch sign, as COBOL stores them. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {}

    /** Decodes a signed zoned field with {@code scale} implied decimals. */
    static BigDecimal decode(String field, int scale) {
        int n = field.length();
        char last = field.charAt(n - 1);
        int d = POS.indexOf(last);
        boolean neg = false;
        if (d < 0) {
            d = NEG.indexOf(last);
            neg = d >= 0;
        }
        if (d < 0) {
            d = last - '0'; // plain digit: positive
        }
        BigInteger v = new BigInteger(field.substring(0, n - 1) + d);
        return new BigDecimal(neg ? v.negate() : v, scale);
    }

    /** Encodes {@code v} (scale 2 assumed) into {@code width} bytes, dropping high-order digits. -0 is stored as +0. */
    static String encode(BigDecimal v, int width) {
        return encodeUnscaled(v.unscaledValue(), width);
    }

    private static String encodeUnscaled(BigInteger unscaled, int width) {
        boolean neg = unscaled.signum() < 0;
        String digits = unscaled.abs().toString();
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        }
        digits = "0".repeat(width - digits.length()) + digits;
        boolean zero = digits.chars().allMatch(c -> c == '0');
        char last = digits.charAt(width - 1);
        String signs = neg && !zero ? NEG : POS;
        return digits.substring(0, width - 1) + signs.charAt(last - '0');
    }

    /** COBOL store into S9(intDigits)V99: high-order truncation, sign kept. */
    static BigDecimal fit(BigDecimal v, int intDigits) {
        BigInteger mod = BigInteger.TEN.pow(intDigits + 2);
        BigInteger u = v.unscaledValue().abs().mod(mod);
        return new BigDecimal(v.signum() < 0 ? u.negate() : u, 2);
    }

    /** Exact quotient truncated toward zero to 2 decimals (COMPUTE without ROUNDED). */
    static BigDecimal truncDivide(BigDecimal num, BigDecimal den) {
        return num.divide(den, 2, RoundingMode.DOWN);
    }
}
