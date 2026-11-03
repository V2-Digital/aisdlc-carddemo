package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text record files: one record per line, LF, ISO-8859-1. */
public final class RecordFiles {
    private RecordFiles() {}

    /** Splits on LF, strips CR, right-pads to {@code lrecl}, ignores a final empty line. */
    public static List<String> load(Path file, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        List<String> records = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            records.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        if (!records.isEmpty() && records.get(records.size() - 1).isEmpty()) {
            records.remove(records.size() - 1);
        }
        records.replaceAll(r -> pad(r, lrecl));
        return records;
    }

    public static void write(Path file, Iterable<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Pads with spaces or cuts to exactly {@code len} characters. */
    public static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    /** Input for a DD: {@code <inputDir>/<DD>.txt} if present, else the sample in {@code dataDir}. */
    public static Path resolve(Path dataDir, Path inputDir, String dd, String sample) {
        if (inputDir != null) {
            Path p = inputDir.resolve(dd + ".txt");
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return dataDir.resolve(sample);
    }
}
