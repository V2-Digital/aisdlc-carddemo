package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text files: one record per line, LF terminated on output. */
final class FixedRecords {
    private FixedRecords() {}

    /** Reads records, stripping a trailing CR and right-padding to {@code length}. */
    static List<String> read(Path file, int length) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) {
            return out;
        }
        for (String line : text.split("\n", -1)) {
            out.add(line);
        }
        if (out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        for (int i = 0; i < out.size(); i++) {
            String l = out.get(i);
            if (l.endsWith("\r")) {
                l = l.substring(0, l.length() - 1);
            }
            out.set(i, pad(l, length));
        }
        return out;
    }

    static String pad(String s, int length) {
        if (s.length() >= length) {
            return s.substring(0, length);
        }
        return s + " ".repeat(length - s.length());
    }

    static void write(Path file, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
