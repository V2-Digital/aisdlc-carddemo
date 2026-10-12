package com.v2.carddemo.cbtrn02c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

/** Canonical text datasets: one fixed-width record per line, LF, ISO-8859-1 (byte = char). */
final class Datasets {
    private Datasets() {}

    static String fit(String line, int lrecl) {
        if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
        if (line.length() >= lrecl) return line.substring(0, lrecl);
        return line + " ".repeat(lrecl - line.length());
    }

    /** Sequential records in file order. A missing file is status 35. */
    static List<String> loadSeq(Path p, int lrecl) {
        List<String> out = new ArrayList<>();
        try {
            String text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
            int start = 0;
            while (start < text.length()) {
                int nl = text.indexOf('\n', start);
                if (nl < 0) nl = text.length();
                out.add(fit(text.substring(start, nl), lrecl));
                start = nl + 1;
            }
        } catch (IOException e) {
            throw new FileStatusException("35");
        }
        return out;
    }

    /** KSDS as a map in byte-compare key order (ISO-8859-1 String order). */
    static TreeMap<String, String> loadKsds(Path p, int lrecl, int keyLen) {
        TreeMap<String, String> m = new TreeMap<>();
        for (String r : loadSeq(p, lrecl)) m.put(r.substring(0, keyLen), r);
        return m;
    }

    /** Write records, each exactly LRECL bytes + LF. An unwritable target is status 30. */
    static void write(Path p, Collection<String> records) {
        StringBuilder sb = new StringBuilder();
        for (String r : records) sb.append(r).append('\n');
        try {
            Files.write(p, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new FileStatusException("30");
        }
    }
}
