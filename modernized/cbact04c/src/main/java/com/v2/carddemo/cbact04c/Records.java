package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text records: one per line, LF, padded to LRECL. */
final class Records {
    private Records() {}

    static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        for (String line : text.split("\n", -1)) {
            line = line.replace("\r", "");
            if (line.isEmpty()) continue;
            out.add(pad(line, lrecl));
        }
        return out;
    }

    static void write(Path file, List<String> recs, int lrecl) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : recs) sb.append(pad(r, lrecl)).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    static String pad(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }

    /** 1-based offset, as in the copybooks. */
    static String slice(String rec, int off, int len) {
        return rec.substring(off - 1, off - 1 + len);
    }

    static String splice(String rec, int off, String val) {
        return rec.substring(0, off - 1) + val + rec.substring(off - 1 + val.length());
    }
}
