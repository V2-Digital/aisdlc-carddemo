package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text datasets: one record per line, LF on write, CR tolerated on read. */
final class Datasets {
    private Datasets() {}

    static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            out.add(line);
        }
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1); // text after the final LF
        }
        return out.stream().map(l -> pad(l, lrecl)).toList();
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
}
