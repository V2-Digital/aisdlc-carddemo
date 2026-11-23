package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width canonical text files: one record per line, LF, padded to LRECL. */
final class Records {
    static final int TCATBAL_LEN = 50;
    static final int XREF_LEN = 50;
    static final int DISCGRP_LEN = 50;
    static final int ACCT_LEN = 300;
    static final int TRAN_LEN = 350;

    private Records() {
    }

    /** Reads a file; returns null when it does not exist (file status 35). */
    static List<String> read(Path path, int lrecl, String dd) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        String text;
        try {
            text = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            return null;
        }
        List<String> out = new ArrayList<>();
        int lineNo = 0;
        for (String line : text.split("\n", -1)) {
            lineNo++;
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty() && lineNo == text.split("\n", -1).length) {
                break; // after the final LF
            }
            if (line.length() > lrecl) {
                throw new Abend("BAD INPUT " + dd + " line " + lineNo + ": longer than " + lrecl);
            }
            out.add(pad(line, lrecl));
        }
        return out;
    }

    static void write(Path path, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    static String pad(String s, int len) {
        return s.length() >= len ? s : s + " ".repeat(len - s.length());
    }

    /** 1-based inclusive columns, as in the spec. */
    static String col(String rec, int from, int to) {
        return rec.substring(from - 1, to);
    }

    static String put(String rec, int from, String value) {
        return rec.substring(0, from - 1) + value + rec.substring(from - 1 + value.length());
    }
}
