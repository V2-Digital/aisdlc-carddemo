package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text records: CR stripped, padded to LRECL on read; LF-terminated on write. */
final class RecordIo {
    private RecordIo() {
    }

    static List<String> read(Path file, int lrecl) throws IOException {
        List<String> records = new ArrayList<>();
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        int start = 0;
        while (start < text.length()) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? text.length() : nl;
            String line = text.substring(start, end);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            records.add(pad(line, lrecl));
            start = end + 1;
        }
        return records;
    }

    static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    static void write(OutputStream out, String record) throws IOException {
        out.write(record.getBytes(StandardCharsets.ISO_8859_1));
        out.write('\n');
    }
}
