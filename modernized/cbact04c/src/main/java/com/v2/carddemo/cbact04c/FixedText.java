package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Canonical fixed-width text files: one record per line, LF, right-padded to LRECL. */
public final class FixedText {
    private FixedText() {}

    public static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            out.add(pad(line, lrecl));
        }
        return out;
    }

    public static void write(Path file, Iterable<String> records, int lrecl) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) sb.append(pad(r, lrecl)).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    public static String pad(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }
}
