package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Canonical text datasets: one fixed-width record per line, LF, ISO-8859-1. */
public final class FixedWidthFiles {
    private FixedWidthFiles() {
    }

    /** Reads records, stripping a trailing CR and right-padding (or cutting) to {@code lrecl}. */
    public static List<String> read(Path file, int lrecl) throws IOException {
        List<String> records = new ArrayList<>();
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        if (text.isEmpty()) {
            return records;
        }
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            records.add(line);
        }
        if (records.getLast().isEmpty()) {
            records.removeLast();
        }
        records.replaceAll(line -> pad(line, lrecl));
        return records;
    }

    public static void write(Path file, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.ISO_8859_1);
    }

    public static String pad(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        return s + " ".repeat(width - s.length());
    }
}
