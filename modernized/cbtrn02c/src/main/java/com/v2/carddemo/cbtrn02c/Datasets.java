package com.v2.carddemo.cbtrn02c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fixed-width text datasets: LF lines, CR stripped on load, records padded to LRECL. */
final class Datasets {
    private Datasets() {}

    /** Path for a DD name from {@code DD_<DD>} in the given environment, or null when unset. */
    static Path ddPath(Map<String, String> env, String dd) {
        String p = env.get("DD_" + dd);
        return p == null ? null : Path.of(p);
    }

    /** Load all records; throws {@link NoSuchFileException} when the file is missing. */
    static List<String> load(Path file, int lrecl) throws IOException {
        String text = Files.readString(file, StandardCharsets.ISO_8859_1).replace("\r", "");
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) {
            return out;
        }
        for (String line : text.split("\n", -1)) {
            out.add(line);
        }
        if (out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        out.replaceAll(l -> fit(l, lrecl));
        return out;
    }

    static String fit(String s, int lrecl) {
        if (s.length() >= lrecl) {
            return s.substring(0, lrecl);
        }
        return s + " ".repeat(lrecl - s.length());
    }

    static void unload(Path file, Iterable<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, sb.toString(), StandardCharsets.ISO_8859_1);
    }
}
