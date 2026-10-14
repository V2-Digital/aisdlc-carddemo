package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Canonical text form: one fixed-width record per line, LF endings. */
public final class FixedFile {
    private FixedFile() {}

    public static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            out.add(line);
        }
        // a trailing newline yields one empty last element
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        out.replaceAll(l -> pad(l, lrecl));
        return out;
    }

    public static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    public static void write(Path file, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.writeString(file, sb, StandardCharsets.ISO_8859_1);
    }
}
