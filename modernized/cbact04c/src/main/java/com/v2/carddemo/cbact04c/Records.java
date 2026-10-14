package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text datasets: strip CR, pad to LRECL on read; LF-terminated on write. */
final class Records {
    private Records() {}

    static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
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

    /** Pads with spaces, or cuts, to exactly {@code len}. */
    static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    /** 1-based inclusive column slice. */
    static String cols(String rec, int from, int to) {
        return rec.substring(from - 1, to);
    }

    /** Replaces 1-based columns starting at {@code from}. */
    static String patch(String rec, int from, String value) {
        return rec.substring(0, from - 1) + value + rec.substring(from - 1 + value.length());
    }
}
