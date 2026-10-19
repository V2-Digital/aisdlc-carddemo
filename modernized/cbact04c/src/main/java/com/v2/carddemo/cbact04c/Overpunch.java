package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned-decimal (DISPLAY) codec with EBCDIC-style overpunch signs: {A-I} = +0..9, }J-R = -0..9. */
public final class Overpunch {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Overpunch() {}

    /** Decodes a signed zoned field with {@code scale} implied decimals. */
    public static BigDecimal decode(String field, int scale) {
        if (field.isEmpty()) throw new IllegalArgumentException("empty numeric field");
        char last = field.charAt(field.length() - 1);
        int p = POS.indexOf(last);
        int n = NEG.indexOf(last);
        int lastDigit = p >= 0 ? p : n >= 0 ? n : Character.digit(last, 10);
        if (lastDigit < 0 || (last < '0' || last > '9') && p < 0 && n < 0) {
            throw new IllegalArgumentException("not numeric: '" + field + "'");
        }
        String body = field.substring(0, field.length() - 1);
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) < '0' || body.charAt(i) > '9') {
                throw new IllegalArgumentException("not numeric: '" + field + "'");
            }
        }
        BigInteger v = new BigInteger(body + lastDigit);
        if (n >= 0) v = v.negate();
        return new BigDecimal(v, scale);
    }

    /** Decodes an unsigned 9(n) field. */
    public static BigDecimal decodeUnsigned(String field, int scale) {
        for (int i = 0; i < field.length(); i++) {
            if (field.charAt(i) < '0' || field.charAt(i) > '9') {
                throw new IllegalArgumentException("not numeric: '" + field + "'");
            }
        }
        return new BigDecimal(new BigInteger(field), scale);
    }

    /** Keeps the low-order {@code digits} digits of the unscaled value (COBOL high-order truncation). */
    public static BigDecimal truncate(BigDecimal v, int digits, int scale) {
        BigInteger u = v.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
        BigInteger mod = BigInteger.TEN.pow(digits);
        BigInteger r = u.abs().mod(mod);
        if (u.signum() < 0) r = r.negate();
        return new BigDecimal(r, scale);
    }

    /** Encodes a signed zoned field of {@code digits} digits (zero is always '{'). */
    public static String encode(BigDecimal v, int digits, int scale) {
        BigDecimal t = truncate(v, digits, scale);
        String s = t.unscaledValue().abs().toString();
        s = "0".repeat(digits - s.length()) + s;
        int d = s.charAt(digits - 1) - '0';
        char sign = t.signum() < 0 ? NEG.charAt(d) : POS.charAt(d);
        return s.substring(0, digits - 1) + sign;
    }

    /** Encodes an unsigned 9(n) field. */
    public static String encodeUnsigned(long v, int digits) {
        String s = Long.toString(Math.floorMod(v, (long) Math.pow(10, digits)));
        return "0".repeat(digits - s.length()) + s;
    }
}
