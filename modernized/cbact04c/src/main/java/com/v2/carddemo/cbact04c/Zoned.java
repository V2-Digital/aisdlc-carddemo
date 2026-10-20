package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Zoned-decimal (EBCDIC overpunch) helpers for PIC S9(n)V99 fields. */
final class Zoned {
    private static final String POS = "{ABCDEFGHI";
    private static final String NEG = "}JKLMNOPQR";

    private Zoned() {
    }

    /** Decodes a signed zoned field with the given number of implied decimals. */
    static BigDecimal decode(String field, int scale) {
        int last = field.length() - 1;
        char c = field.charAt(last);
        int sign = 1;
        int digit = POS.indexOf(c);
        if (digit < 0) {
            digit = NEG.indexOf(c);
            sign = -1;
        }
        if (digit < 0) {
            throw new IllegalArgumentException("Not a signed zoned digit: " + c);
        }
        for (int i = 0; i < last; i++) {
            if (field.charAt(i) < '0' || field.charAt(i) > '9') {
                throw new IllegalArgumentException("Not numeric: " + field);
            }
        }
        BigInteger unscaled = new BigInteger(field.substring(0, last) + digit);
        return new BigDecimal(unscaled.multiply(BigInteger.valueOf(sign)), scale);
    }

    /** Encodes {@code v} (already at {@code scale}) into {@code len} bytes; the sign sits in the last byte. */
    static String encode(BigDecimal v, int len) {
        String digits = v.unscaledValue().abs().toString();
        digits = "0".repeat(len - digits.length()) + digits;
        String signs = v.signum() < 0 ? NEG : POS;
        return digits.substring(0, len - 1) + signs.charAt(digits.charAt(len - 1) - '0');
    }

    /** COBOL silent high-order truncation: keeps the sign, drops integer digits above {@code intDigits}. */
    static BigDecimal truncate(BigDecimal v, int intDigits) {
        return v.remainder(BigDecimal.TEN.pow(intDigits));
    }
}
