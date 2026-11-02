package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Canonical text datasets: one fixed-width record per line, LF, no CR. */
final class Datasets {
    private Datasets() {
    }

    /**
     * Loads {@code <dir>/<dd>.txt} as a key-sequenced map (KSDS order = key order).
     * Returns null if the file does not exist (OPEN status 35).
     */
    static TreeMap<String, String> load(Path dir, String dd, int lrecl, int keyLen) throws IOException {
        Path p = dir.resolve(dd + ".txt");
        if (!Files.isRegularFile(p)) {
            return null;
        }
        TreeMap<String, String> m = new TreeMap<>();
        for (String line : Files.readAllLines(p, StandardCharsets.ISO_8859_1)) {
            line = line.replace("\r", "");
            if (line.isEmpty()) {
                continue;
            }
            String rec = Records.pad(line, lrecl);
            m.put(rec.substring(0, keyLen), rec);
        }
        return m;
    }

    static void write(Path file, Iterable<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    static void write(Path file, Map<String, String> ksds) throws IOException {
        write(file, ksds.values());
    }

    static List<String> empty() {
        return List.of();
    }
}
