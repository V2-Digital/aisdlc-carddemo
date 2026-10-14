package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed-offset field access (0-based substring bounds; offsets from the copybooks). */
final class Records {
    private Records() {}

    static final int TCATBAL_LEN = 50, XREF_LEN = 50, ACCT_LEN = 300, DISC_LEN = 50, TRAN_LEN = 350;

    // CVACT01Y: ACCT-CURR-BAL @13(12), CYC-CREDIT @79(12), CYC-DEBIT @91(12), ACCT-GROUP-ID @113(10)
    static final int BAL = 12, CREDIT = 78, DEBIT = 90, GROUP = 112;

    static String pad(String s, int len) {
        return s.length() >= len ? s.substring(0, len) : s + " ".repeat(len - s.length());
    }

    static List<String> read(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readString(file, StandardCharsets.ISO_8859_1).split("\n", -1)) {
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.isEmpty()) continue;
            out.add(pad(line, lrecl));
        }
        return out;
    }

    static void write(Path file, List<String> recs) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : recs) sb.append(r).append('\n');
        Files.writeString(file, sb, StandardCharsets.ISO_8859_1);
    }

    static String put(String rec, int off, String v) {
        return rec.substring(0, off) + v + rec.substring(off + v.length());
    }
}
