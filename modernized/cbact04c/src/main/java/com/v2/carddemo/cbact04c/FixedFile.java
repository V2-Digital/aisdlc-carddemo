package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width, LF-separated text records (the harness canonical form). */
final class FixedFile {
    private FixedFile() {}

    static List<String> read(Path path, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
        List<String> records = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int nl = text.indexOf('\n', pos);
            String line = nl < 0 ? text.substring(pos) : text.substring(pos, nl);
            pos = nl < 0 ? text.length() : nl + 1;
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            records.add(pad(line, lrecl));
        }
        return records;
    }

    static void write(Path path, Iterable<String> records, int lrecl) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(pad(r, lrecl)).append('\n');
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }
}
