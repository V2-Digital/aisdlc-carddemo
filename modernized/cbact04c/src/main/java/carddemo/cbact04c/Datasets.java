package carddemo.cbact04c;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Canonical text datasets: one fixed-width record per line, LF endings. Located by DD name. */
record Datasets(Map<String, Path> dd) {

    static final String TCATBALF = "TCATBALF", XREFFILE = "XREFFILE", ACCTFILE = "ACCTFILE",
            DISCGRP = "DISCGRP", TRANSACT = "TRANSACT";

    static Datasets fromEnv() {
        var m = new java.util.HashMap<String, Path>();
        for (String n : List.of(TCATBALF, XREFFILE, ACCTFILE, DISCGRP, TRANSACT)) {
            String v = System.getenv("DD_" + n);
            if (v != null) {
                m.put(n, Path.of(v));
            }
        }
        return new Datasets(m);
    }

    /** Reads records, stripping a trailing CR and padding to {@code lrecl}. Null if the DD is unusable. */
    List<String> read(String name, int lrecl) {
        Path p = dd.get(name);
        if (p == null || !Files.isRegularFile(p)) {
            return null;
        }
        try {
            List<String> out = new ArrayList<>();
            for (String line : Files.readAllLines(p, StandardCharsets.ISO_8859_1)) {
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                out.add(pad(line, lrecl));
            }
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    /** Creates or truncates {@code name}. False if the DD is unusable. */
    boolean canWrite(String name) {
        Path p = dd.get(name);
        if (p == null) {
            return false;
        }
        try {
            Files.write(p, new byte[0]);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    void write(String name, List<String> records) {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        try {
            Files.write(dd.get(name), sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String pad(String s, int n) {
        if (s.length() >= n) {
            return s.substring(0, n);
        }
        return s + " ".repeat(n - s.length());
    }
}
