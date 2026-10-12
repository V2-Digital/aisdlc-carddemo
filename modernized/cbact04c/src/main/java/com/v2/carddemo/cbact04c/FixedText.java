package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Canonical text form: one fixed-width record per line, ISO-8859-1, LF terminated. */
final class FixedText {
    private FixedText() {}

    /** Reads records, stripping a trailing CR and right-padding to {@code lrecl}. */
    static List<String> read(Path p, int lrecl) throws IOException {
        String all = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        int pos = 0;
        while (pos < all.length()) {
            int nl = all.indexOf('\n', pos);
            String line = nl < 0 ? all.substring(pos) : all.substring(pos, nl);
            pos = nl < 0 ? all.length() : nl + 1;
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.length() > lrecl) throw new IOException("record longer than LRECL " + lrecl);
            out.add(pad(line, lrecl));
        }
        return out;
    }

    static String pad(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    static void write(OutputStream os, String rec) throws IOException {
        os.write(rec.getBytes(StandardCharsets.ISO_8859_1));
        os.write('\n');
    }
}
