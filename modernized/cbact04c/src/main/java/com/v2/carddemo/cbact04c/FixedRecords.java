package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width LF text datasets ("unload form"). */
final class FixedRecords {
    private FixedRecords() {}

    static List<String> read(Path file, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        List<String> records = new ArrayList<>();
        if (text.isEmpty()) {
            return records;
        }
        for (String line : text.split("\n", -1)) {
            records.add(fit(line.replace("\r", ""), lrecl));
        }
        if (records.get(records.size() - 1).isBlank() && text.endsWith("\n")) {
            records.remove(records.size() - 1);
        }
        return records;
    }

    static String fit(String s, int lrecl) {
        if (s.length() >= lrecl) {
            return s.substring(0, lrecl);
        }
        return s + " ".repeat(lrecl - s.length());
    }

    static void write(Path file, Iterable<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
