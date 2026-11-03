package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text-file helpers: one record per line, LF, right-padded to the record length. */
final class Records {
    private Records() {}

    /** Reads {@code file}; strips CR, pads (or cuts) each line to {@code lrecl}. */
    static List<String> read(Path file, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            out.add(line);
        }
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        out.replaceAll(l -> pad(l, lrecl));
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

    /** 1-based COBOL-style slice: {@code offset} 1-based, {@code len} bytes. */
    static String field(String rec, int offset, int len) {
        return rec.substring(offset - 1, offset - 1 + len);
    }

    /** Returns {@code rec} with {@code value} (exactly its length) overlaid at 1-based {@code offset}. */
    static String put(String rec, int offset, String value) {
        return rec.substring(0, offset - 1) + value + rec.substring(offset - 1 + value.length());
    }
}
