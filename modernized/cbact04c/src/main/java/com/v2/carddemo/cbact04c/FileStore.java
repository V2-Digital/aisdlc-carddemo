package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Canonical text datasets: one fixed-width record per line, LF. Bytes are handled as ISO-8859-1. */
public final class FileStore {
    private FileStore() {}

    /** Reads records, stripping CR and right-padding short lines to {@code lrecl}. */
    public static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                continue;
            }
            out.add(Records.pad(line, lrecl));
        }
        return out;
    }

    public static void write(Path file, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.ISO_8859_1);
    }
}
