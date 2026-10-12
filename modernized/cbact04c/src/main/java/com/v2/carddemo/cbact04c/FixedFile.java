package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text datasets: one record per line, LF, ISO-8859-1. */
final class FixedFile {
    private FixedFile() {}

    static List<String> read(Path p, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int nl = text.indexOf('\n', start);
            String line = nl < 0 ? text.substring(start) : text.substring(start, nl);
            start = nl < 0 ? text.length() : nl + 1;
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.length() > lrecl) {
                throw new IOException(p.getFileName() + ": record longer than LRECL " + lrecl);
            }
            out.add(line + " ".repeat(lrecl - line.length()));
        }
        return out;
    }

    static byte[] toBytes(List<String> records) {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Writes via temp file and atomic rename. */
    static void write(Path p, List<String> records) throws IOException {
        Path abs = p.toAbsolutePath();
        Path tmp = Files.createTempFile(abs.getParent(), ".cbact04c", ".tmp");
        try {
            Files.write(tmp, toBytes(records));
            Files.move(tmp, abs, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
