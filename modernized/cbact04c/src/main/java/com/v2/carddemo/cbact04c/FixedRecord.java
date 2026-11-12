package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text record helpers (one record per LF line, ISO-8859-1). */
final class FixedRecord {
    private FixedRecord() {}

    /** Reads all lines, strips a trailing CR and right-pads each to {@code lrecl}. */
    static List<String> readAll(Path file, int lrecl) throws IOException {
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int nl = text.indexOf('\n', pos);
            String line = nl < 0 ? text.substring(pos) : text.substring(pos, nl);
            pos = nl < 0 ? text.length() : nl + 1;
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            out.add(pad(line, lrecl));
        }
        return out;
    }

    static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    /** Field by 1-based column and length. */
    static String get(String rec, int col, int len) {
        return rec.substring(col - 1, col - 1 + len);
    }

    /** Returns the record with the field at 1-based column replaced. */
    static String put(String rec, int col, String value) {
        return rec.substring(0, col - 1) + value + rec.substring(col - 1 + value.length());
    }

    static void writeAll(Path file, List<String> records, int lrecl) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            if (r.length() != lrecl) {
                throw new IllegalStateException("record length " + r.length() + " != " + lrecl);
            }
            sb.append(r).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.ISO_8859_1);
    }
}
