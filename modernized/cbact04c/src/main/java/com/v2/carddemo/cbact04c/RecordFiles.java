package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text datasets: LF separated, optional CR stripped, short lines padded. ISO-8859-1 keeps one char per byte. */
public final class RecordFiles {
    private RecordFiles() {}

    public static List<String> read(Path file, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int nl = text.indexOf('\n', pos);
            String line = nl < 0 ? text.substring(pos) : text.substring(pos, nl);
            pos = nl < 0 ? text.length() : nl + 1;
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            out.add(pad(line, lrecl));
        }
        return out;
    }

    public static void write(Path file, Iterable<String> records, int lrecl) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) sb.append(pad(r, lrecl), 0, lrecl).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    public static String pad(String s, int len) {
        if (s.length() >= len) return s;
        return s + " ".repeat(len - s.length());
    }
}
