package carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Canonical text datasets: one fixed-width record per LF-terminated line, ISO-8859-1. */
final class FixedFile {
    private FixedFile() {}

    /** Reads records, stripping CR and right-padding to lrecl. Longer lines are an error. */
    static List<String> read(Path file, int lrecl) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int nl = text.indexOf('\n', pos);
            int end = nl < 0 ? text.length() : nl;
            String line = text.substring(pos, end);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.length() > lrecl) {
                throw new IOException("Record longer than LRECL " + lrecl + " in " + file);
            }
            out.add(line + " ".repeat(lrecl - line.length()));
            pos = end + 1;
        }
        return out;
    }

    static void write(Path file, Iterable<String> records) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (String r : records) {
            bos.writeBytes(r.getBytes(StandardCharsets.ISO_8859_1));
            bos.write('\n');
        }
        Files.write(file, bos.toByteArray());
    }
}
