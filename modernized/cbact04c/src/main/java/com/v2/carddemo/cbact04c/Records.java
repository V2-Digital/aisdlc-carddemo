package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width record helpers and field offsets (see spec.md). */
final class Records {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int DISCGRP_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int TRAN_LEN = 350;

    private Records() {}

    /** Reads lines, strips trailing CR, right-pads to {@code lrecl}. Blank lines are skipped. */
    static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                continue;
            }
            out.add(pad(line, lrecl));
        }
        return out;
    }

    static void write(Path file, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    static BigDecimal catBal(String rec) {
        return Zoned.decode(rec.substring(17, 28), 2);
    }

    static BigDecimal rate(String rec) {
        return Zoned.decode(rec.substring(16, 22), 2);
    }

    static BigDecimal acctBal(String rec) {
        return Zoned.decode(rec.substring(12, 24), 2);
    }

    /** New ACCTFILE record: balance replaced, cycle credit/debit set to +0. */
    static String rewriteAccount(String rec, BigDecimal newBal) {
        String zero = Zoned.encode(BigDecimal.ZERO, 12, 2);
        return rec.substring(0, 12) + Zoned.encode(newBal, 12, 2) + rec.substring(24, 78)
                + zero + zero + rec.substring(102);
    }
}
