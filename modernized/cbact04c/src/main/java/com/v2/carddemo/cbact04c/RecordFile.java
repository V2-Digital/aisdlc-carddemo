package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text datasets: one record per line, ISO-8859-1, LF on output. */
final class RecordFile {
    private RecordFile() {}

    /** Reads records, stripping a trailing CR and right-padding short lines to {@code lrecl}. */
    static List<String> read(Path path, int lrecl) throws IOException {
        String text = Files.readString(path, StandardCharsets.ISO_8859_1);
        List<String> records = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? text.length() : nl;
            String line = text.substring(start, end);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.length() > lrecl) {
                throw new IOException("record longer than LRECL " + lrecl + " in " + path);
            }
            records.add(pad(line, lrecl));
            start = end + 1;
        }
        return records;
    }

    /** Writes records to a temp file next to {@code path}, then atomically renames it over {@code path}. */
    static void write(Path path, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Path abs = path.toAbsolutePath();
        Path tmp = Files.createTempFile(abs.getParent(), abs.getFileName().toString(), ".tmp");
        Files.writeString(tmp, sb, StandardCharsets.ISO_8859_1);
        Files.move(tmp, abs, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }
}
