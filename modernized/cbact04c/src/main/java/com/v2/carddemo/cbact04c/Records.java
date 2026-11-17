package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width text file I/O (harness canonical form) and record layouts. */
final class Records {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int DISCGRP_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int TRAN_LEN = 350;

    private Records() {}

    /** Strips CR, right-pads to lrecl, skips empty lines. Result is not sorted. */
    static List<String> load(Path p, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(p, StandardCharsets.ISO_8859_1)) {
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

    static void write(Path p, List<String> recs) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : recs) {
            sb.append(r).append('\n');
        }
        Files.write(p, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
