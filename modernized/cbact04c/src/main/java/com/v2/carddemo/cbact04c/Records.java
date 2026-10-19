package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text records: one per line, LF on write; CR stripped and short lines padded on read. */
public final class Records {
    private Records() {}

    public static List<String> read(Path file, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int nl = text.indexOf('\n', start);
            String line = nl < 0 ? text.substring(start) : text.substring(start, nl);
            start = nl < 0 ? text.length() : nl + 1;
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.length() > lrecl) line = line.substring(0, lrecl);
            out.add(pad(line, lrecl));
        }
        return out;
    }

    public static void write(Path file, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) sb.append(r).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    public static String pad(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }

    /** 1-based offset, length substring, as in the copybook layouts. */
    public static String field(String rec, int offset, int len) {
        return rec.substring(offset - 1, offset - 1 + len);
    }

    public static String put(String rec, int offset, String value) {
        return rec.substring(0, offset - 1) + value + rec.substring(offset - 1 + value.length());
    }
}
