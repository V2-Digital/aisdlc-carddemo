package carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Text datasets: one fixed-width record per line, LF, ISO-8859-1. */
final class Datasets {
    private Datasets() {}

    static List<String> read(Path p, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
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
        out.replaceAll(l -> pad(l.replace("\r", ""), lrecl));
        return out;
    }

    static void write(Path p, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String r : records) {
            sb.append(r).append('\n');
        }
        Files.write(p, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    static String pad(String s, int n) {
        return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length());
    }
}
