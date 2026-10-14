package carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test helper: lays out DD files in a temp dir and runs the program. */
final class Fx {
    static final Path ROOT = Path.of(System.getProperty("repo.root")).toAbsolutePath().normalize();
    static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2022, 7, 18, 0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    record Result(int rc, String sysout, Path dir) {
        String file(String dd) { return Fx.read(dir.resolve(dd + ".txt")); }
        List<String> lines(String dd) { return file(dd).lines().toList(); }
    }

    static String read(Path p) {
        try { return new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    static void write(Path p, String s) {
        try { Files.write(p, s.getBytes(StandardCharsets.ISO_8859_1)); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    /** inputs: DD name to file content (or null to leave the DD absent). TRANSACT is always provided. */
    static Result run(Path dir, Map<String, String> inputs) {
        Map<String, Path> dd = new HashMap<>();
        inputs.forEach((k, v) -> {
            if (v != null) { write(dir.resolve(k + ".txt"), v); }
            dd.put(k, dir.resolve(k + ".txt"));
        });
        dd.put("TRANSACT", dir.resolve("TRANSACT.txt"));
        return run(dir, new Datasets(dd));
    }

    static Result run(Path dir, Datasets ds) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos, true, StandardCharsets.ISO_8859_1);
        PrintStream err = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        int rc;
        try { rc = Cbact04c.run("2022071800", ds, CLOCK, ps); }
        finally { System.setErr(err); }
        ps.flush();
        return new Result(rc, bos.toString(StandardCharsets.ISO_8859_1), dir);
    }

    // record builders (fixed width)
    static String tcat(String acct, String type, String cat, String bal) {
        return Datasets.pad(acct + type + cat + bal, 50) + "\n";
    }
    static String xref(String card, String acct) {
        return Datasets.pad(card + " ".repeat(9) + acct, 50) + "\n";
    }
    static String acct(String id, String bal, String credit, String debit, String group) {
        char[] c = " ".repeat(300).toCharArray();
        put(c, 0, id); put(c, 11, "1"); put(c, 12, z12(bal)); put(c, 75, z12(credit)); put(c, 87, z12(debit)); put(c, 112, group);
        return new String(c) + "\n";
    }
    static String disc(String group, String type, String cat, String rate) {
        return Datasets.pad(Datasets.pad(group, 10) + type + cat + rate, 50) + "\n";
    }
    private static String z12(String v) { return "0".repeat(12 - v.length()) + v; }
    private static void put(char[] c, int off, String s) { s.getChars(0, s.length(), c, off); }
}
