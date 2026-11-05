package carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test fixture: temp dataset dir, in-process run, helpers to build fixed-width records. */
final class Fx {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final String CLOCK = "2022071800000000";
    final Path dir;
    int rc;
    String sysout;

    Fx(Path dir) {
        this.dir = dir;
    }

    static String pad(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    static String acct(String id, String balZoned, String group) {
        StringBuilder sb = new StringBuilder(pad(id + "Y" + "0" + balZoned, 300));
        sb.replace(78, 90, "00000010200{");
        sb.replace(90, 102, "00000020200{");
        sb.replace(112, 122, pad(group, 10));
        return sb.toString();
    }

    static String tcat(String acct, String type, String cat, String balZoned) {
        return pad(acct + type + cat + balZoned, 50);
    }

    static String xref(String card, String acct) {
        return pad(card + "         " + acct, 50);
    }

    static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + rate, 50);
    }

    void put(String dd, List<String> lines) throws IOException {
        Files.writeString(dir.resolve(dd + ".txt"), String.join("\n", lines) + "\n", StandardCharsets.ISO_8859_1);
    }

    void copy(Path src, String dd) throws IOException {
        Files.copy(src, dir.resolve(dd + ".txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    String get(String dd) throws IOException {
        return Files.readString(dir.resolve(dd + ".txt"), StandardCharsets.ISO_8859_1);
    }

    List<String> lines(String dd) throws IOException {
        return get(dd).lines().toList();
    }

    Fx run() {
        Map<String, String> env = new HashMap<>();
        for (String dd : List.of("TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE", "TRANSACT")) {
            env.put("DD_" + dd, dir.resolve(dd + ".txt").toString());
        }
        env.put("CARDDEMO_CURRENT_DATE", CLOCK);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bo, false, StandardCharsets.ISO_8859_1);
        rc = Cbact04c.run(env, "2022071800", ps);
        ps.flush();
        sysout = bo.toString(StandardCharsets.ISO_8859_1);
        return this;
    }

    /** Two-account standard fixture: acct 1 (rate lines for 01/0001), acct 2 last. */
    Fx basic(String bal1, String bal2, String group) throws IOException {
        put("TCATBALF", List.of(tcat("00000000001", "01", "0001", bal1), tcat("00000000002", "01", "0001", bal2)));
        put("XREFFILE", List.of(xref("4111111111111111", "00000000001"), xref("4222222222222222", "00000000002")));
        put("DISCGRP", List.of(disc("DEFAULT", "01", "0001", "00150{"), disc("GRP1", "01", "0001", "00200{")));
        put("ACCTFILE", List.of(acct("00000000001", "0000100000{", group), acct("00000000002", "0000100000{", group)));
        return this;
    }
}
