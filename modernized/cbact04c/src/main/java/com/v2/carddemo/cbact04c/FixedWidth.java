package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width LF text files, the harness's canonical form. */
public final class FixedWidth {
    private FixedWidth() {}

    public static List<String> read(Path file, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).replace("\r", "");
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) {
            return out;
        }
        for (String line : text.split("\n", -1)) {
            out.add(pad(line, lrecl));
        }
        if (text.endsWith("\n")) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    public static void write(Path file, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Right-pads with spaces, or cuts, to {@code len}. */
    public static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    /** Replaces {@code value} at {@code offset}, keeping every other byte. */
    public static String overlay(String record, int offset, String value) {
        return record.substring(0, offset) + value + record.substring(offset + value.length());
    }
}
