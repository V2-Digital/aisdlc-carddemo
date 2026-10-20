package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Fixed-width text datasets: one record per line, LF, ISO-8859-1. */
final class Datasets {
    private Datasets() {}

    /** Load into a key-ordered map (KSDS). Strips CR, pads to {@code lrecl}. */
    static TreeMap<String, String> loadKsds(Path file, int lrecl, int keyLen) throws IOException {
        TreeMap<String, String> map = new TreeMap<>();
        for (String rec : loadLines(file, lrecl)) {
            map.put(rec.substring(0, keyLen), rec);
        }
        return map;
    }

    static List<String> loadLines(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            line = line.replace("\r", "");
            if (line.length() > lrecl) {
                line = line.substring(0, lrecl);
            }
            out.add(line + " ".repeat(lrecl - line.length()));
        }
        return out;
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
}
