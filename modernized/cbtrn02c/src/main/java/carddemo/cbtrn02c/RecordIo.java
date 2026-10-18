package carddemo.cbtrn02c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Canonical text files: one fixed-width record per line, LF (CR stripped on read). */
public final class RecordIo {
    private RecordIo() {}

    public static List<byte[]> read(Path path, int lrecl) throws IOException {
        byte[] all = Files.readAllBytes(path);
        List<byte[]> recs = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= all.length; i++) {
            if (i == all.length || all[i] == '\n') {
                if (i == all.length && start == i) {
                    break;
                }
                int end = i;
                if (end > start && all[end - 1] == '\r') {
                    end--;
                }
                recs.add(pad(Arrays.copyOfRange(all, start, end), lrecl));
                start = i + 1;
            }
        }
        return recs;
    }

    public static byte[] pad(byte[] line, int lrecl) {
        byte[] r = new byte[lrecl];
        Arrays.fill(r, (byte) ' ');
        System.arraycopy(line, 0, r, 0, Math.min(line.length, lrecl));
        return r;
    }

    public static void write(Path path, Iterable<byte[]> recs) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] r : recs) {
            out.write(r);
            out.write('\n');
        }
        Files.write(path, out.toByteArray());
    }

    public static String str(byte[] rec, int off, int len) {
        return new String(rec, off, len, StandardCharsets.ISO_8859_1);
    }

    public static void put(byte[] rec, int off, String s) {
        System.arraycopy(s.getBytes(StandardCharsets.ISO_8859_1), 0, rec, off, s.length());
    }
}
