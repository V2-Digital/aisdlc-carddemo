package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text records: one per line, LF on output, CR tolerated on input. */
final class FixedFile {
    private FixedFile() {}

    /** Thrown when a line is longer than LRECL. */
    static final class TooLong extends IOException {
        TooLong(String m) { super(m); }
    }

    static List<String> read(Path p, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int nl = text.indexOf('\n', pos);
            String line = nl < 0 ? text.substring(pos) : text.substring(pos, nl);
            pos = nl < 0 ? text.length() : nl + 1;
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.length() > lrecl) {
                throw new TooLong(p.getFileName() + ": record longer than " + lrecl);
            }
            out.add(line + " ".repeat(lrecl - line.length()));
        }
        return out;
    }

    static void write(Path p, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(p, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
