package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text records: read (strip CR, pad to LRECL) and write (LRECL + LF, ISO-8859-1). */
final class Records {
    private Records() {}

    static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                continue;
            }
            out.add(pad(line, lrecl));
        }
        return out;
    }

    static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    static byte[] write(List<String> records, int lrecl) {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(pad(r, lrecl)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}
