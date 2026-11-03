package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Canonical text datasets: one fixed-width record per LF-terminated line. */
final class Datasets {
    private Datasets() {}

    /** Read records, strip CR, pad (or cut) to {@code lrecl}. Returns null if the file is missing. */
    static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
                if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                out.add(pad(line, lrecl));
            }
        } catch (NoSuchFileException e) {
            return null;
        }
        return out;
    }

    /** KSDS load: records keyed by {@code [keyStart, keyStart+keyLen)}, in key order. */
    static TreeMap<String, String> readKeyed(Path file, int lrecl, int keyStart, int keyLen) throws IOException {
        List<String> recs = read(file, lrecl);
        if (recs == null) return null;
        TreeMap<String, String> m = new TreeMap<>();
        for (String r : recs) m.put(r.substring(keyStart, keyStart + keyLen), r);
        return m;
    }

    static void write(Path file, Iterable<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) sb.append(r).append('\n');
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, sb, StandardCharsets.ISO_8859_1);
    }

    static String pad(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    static void writeMap(Path file, Map<String, String> m) throws IOException {
        write(file, m.values());
    }
}
