package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width record file access: one record per line, CR stripped, padded to LRECL. */
final class Records {
    static final int TCATBALF = 50;
    static final int XREFFILE = 50;
    static final int DISCGRP = 50;
    static final int ACCTFILE = 300;
    static final int TRANSACT = 350;

    private Records() {}

    /** Reads {@code <dir>/<dd>.txt}; throws {@link NoSuchFileException} if absent. */
    static List<String> read(Path dir, String dd, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        String text = Files.readString(dir.resolve(dd + ".txt"), StandardCharsets.ISO_8859_1);
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

    static void write(Path dir, String dd, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.writeString(dir.resolve(dd + ".txt"), sb, StandardCharsets.ISO_8859_1);
    }

    static String pad(String s, int len) {
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    /** 1-based column and length, as in the copybook comments. */
    static String field(String rec, int col, int len) {
        return rec.substring(col - 1, col - 1 + len);
    }

    static String put(String rec, int col, String value) {
        return rec.substring(0, col - 1) + value + rec.substring(col - 1 + value.length());
    }
}
