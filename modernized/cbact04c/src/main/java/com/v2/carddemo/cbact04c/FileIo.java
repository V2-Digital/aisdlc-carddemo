package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Canonical text datasets: one fixed-width record per line, LF, ISO-8859-1. */
final class FileIo {
    private FileIo() {}

    static List<String> read(Path file, int lrecl) throws IOException {
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                continue;
            }
            out.add(Zoned.right(line, lrecl));
        }
        return out;
    }

    static void write(Path file, Iterable<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.writeString(file, sb, StandardCharsets.ISO_8859_1);
    }
}
