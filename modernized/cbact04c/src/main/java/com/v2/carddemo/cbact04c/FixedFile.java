package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Canonical text form: one fixed-width record per line, LF. */
final class FixedFile {
    private FixedFile() {}

    static List<String> read(Path file, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.isEmpty()) continue;
            out.add(line.length() >= lrecl ? line.substring(0, lrecl) : line + " ".repeat(lrecl - line.length()));
        }
        return out;
    }

    static void write(Path file, Collection<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) sb.append(r).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
