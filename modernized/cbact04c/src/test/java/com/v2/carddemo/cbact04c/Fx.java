package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Shared helpers: run Main in-process with a frozen clock and capture SYSOUT. */
final class Fx {
    static final Path ROOT = Path.of(System.getProperty("repo.root", "../..")).toAbsolutePath().normalize();
    static final String CLOCK = "2022071800000000";

    record Run(int rc, String sysout, Path out) {
        String file(String name) throws IOException {
            return Files.readString(out.resolve(name), StandardCharsets.ISO_8859_1);
        }
    }

    static Run run(Path out, Path inputDir, String dataDir) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf, false, StandardCharsets.ISO_8859_1);
        Map<String, String> env = Map.of("CARDDEMO_CURRENT_DATE", CLOCK, "DATA_DIR", dataDir);
        String[] args = inputDir == null
                ? new String[] {"2022071800", out.toString()}
                : new String[] {"2022071800", out.toString(), inputDir.toString()};
        int rc = Main.run(args, env::get, ps);
        ps.flush();
        return new Run(rc, buf.toString(StandardCharsets.ISO_8859_1), out);
    }

    static Run runStock(Path out, Path inputDir) throws IOException {
        return run(out, inputDir, ROOT.resolve("app/data/ASCII").toString());
    }

    static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + bal, 50);
    }

    static String acct(String id, String bal, String group) {
        char[] c = pad(id + "Y" + bal, 300).toCharArray();
        group.getChars(0, group.length(), c, 112);
        return new String(c);
    }

    static String xref(String card, String acct) {
        return pad(card + "000000000", 25) + acct + " ".repeat(14);
    }

    static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + rate, 50);
    }

    static void write(Path dir, String dd, String... recs) throws IOException {
        Files.writeString(dir.resolve(dd + ".txt"), String.join("\n", recs) + "\n", StandardCharsets.ISO_8859_1);
    }

    static final String BAL0 = "00000010000{"; // 1000.00
}
