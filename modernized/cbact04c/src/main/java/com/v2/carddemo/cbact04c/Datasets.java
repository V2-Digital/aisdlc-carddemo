package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fixed-width text datasets, in the harness/golden format. */
final class Datasets {
    private Datasets() {
    }

    /** Reads records: CR stripped, right-padded with spaces to {@code lrecl}. */
    static List<String> read(Path file, int lrecl) throws IOException {
        List<String> records = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            line = line.replace("\r", "");
            if (line.length() > lrecl) {
                line = line.substring(0, lrecl);
            }
            records.add(line + " ".repeat(lrecl - line.length()));
        }
        return records;
    }

    /** Writes records, each followed by LF. */
    static void write(Path file, Iterable<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.writeString(file, sb, StandardCharsets.ISO_8859_1);
    }

    /** Loads records into a map keyed by {@code record[0..keyLen)}; the first of duplicate keys wins. */
    static void index(List<String> records, int keyOff, int keyLen, Map<String, String> into) {
        for (String r : records) {
            into.putIfAbsent(r.substring(keyOff, keyOff + keyLen), r);
        }
    }
}
