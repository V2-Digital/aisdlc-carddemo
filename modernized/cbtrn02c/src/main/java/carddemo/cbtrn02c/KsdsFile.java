package carddemo.cbtrn02c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Fixed-width text dataset helpers plus an in-memory KSDS with COBOL file status codes. */
class KsdsFile {
    private final int keyLen;
    private final TreeMap<String, String> records = new TreeMap<>();

    KsdsFile(int keyLen) {
        this.keyLen = keyLen;
    }

    /** Read lines: strip CR, pad to lrecl, reject longer lines. */
    static List<String> readLines(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        int start = 0;
        while (start < text.length()) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? text.length() : nl;
            String line = text.substring(start, end).replace("\r", "");
            start = end + 1;
            if (line.length() > lrecl) {
                throw new LoadException(file.getFileName() + ": line longer than LRECL " + lrecl);
            }
            out.add(pad(line, lrecl));
        }
        return out;
    }

    static String pad(String s, int len) {
        return s.length() >= len ? s : s + " ".repeat(len - s.length());
    }

    static void writeLines(Path file, List<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        Files.writeString(file, sb, StandardCharsets.ISO_8859_1);
    }

    static KsdsFile load(Path file, int lrecl, int keyLen) throws IOException {
        KsdsFile f = new KsdsFile(keyLen);
        for (String line : readLines(file, lrecl)) {
            if (f.records.put(line.substring(0, keyLen), line) != null) {
                throw new LoadException(file.getFileName() + ": duplicate key " + line.substring(0, keyLen));
            }
        }
        return f;
    }

    /** @return record or null (status 23) */
    String read(String key) {
        return records.get(key);
    }

    /** @return status 00, or 22 for a duplicate key */
    String write(String record) {
        return records.putIfAbsent(record.substring(0, keyLen), record) == null ? "00" : "22";
    }

    /** @return status 00, or 23 when the record does not exist */
    String rewrite(String record) {
        String key = record.substring(0, keyLen);
        if (!records.containsKey(key)) {
            return "23";
        }
        records.put(key, record);
        return "00";
    }

    void save(Path file) throws IOException {
        writeLines(file, new ArrayList<>(records.values()));
    }

    Map<String, String> all() {
        return records;
    }
}
