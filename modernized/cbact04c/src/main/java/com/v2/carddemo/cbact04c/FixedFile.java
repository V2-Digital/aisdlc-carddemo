package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Canonical text datasets: one fixed-width record per line, LF endings. */
public final class FixedFile {
    private FixedFile() {}

    /** Reads records, accepting CRLF and short lines (right-padded with spaces to {@code lrecl}). */
    public static List<String> read(Path file, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        List<String> records = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            records.add(line);
        }
        if (!records.isEmpty() && records.get(records.size() - 1).isEmpty()) {
            records.remove(records.size() - 1);
        }
        records.replaceAll(r -> pad(r, lrecl));
        return records;
    }

    public static void write(Path file, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    public static String pad(String s, int len) {
        return s.length() >= len ? s : s + " ".repeat(len - s.length());
    }
}
