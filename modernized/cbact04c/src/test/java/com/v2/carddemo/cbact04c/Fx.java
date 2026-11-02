package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test fixtures: record builders and a runner. */
final class Fx {
    static final String TS = "2022071800000000";
    static final Path ROOT = Path.of(System.getProperty("repo.root", "../..")).normalize();

    record Run(int rc, String sysout, Path out) {
        List<String> lines(String dd) throws IOException {
            return Files.readAllLines(out.resolve(dd + ".txt"));
        }
    }

    static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    static String z(String v, int digits, int scale) {
        return Zoned.encode(new BigDecimal(v), digits, scale);
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + z(bal, 11, 2), 50);
    }

    static String xref(String card, String acct) {
        return pad(card + "000000001" + acct, 50);
    }

    static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + z(rate, 6, 2), 50);
    }

    static String acct(String id, String bal, String credit, String debit, String group) {
        char[] c = " ".repeat(300).toCharArray();
        put(c, 1, id);
        put(c, 12, "Y");
        put(c, 13, z(bal, 12, 2));
        put(c, 79, z(credit, 12, 2));
        put(c, 91, z(debit, 12, 2));
        put(c, 113, pad(group, 10));
        return new String(c);
    }

    private static void put(char[] c, int col, String s) {
        s.getChars(0, s.length(), c, col - 1);
    }

    static Path dir(Path tmp, String name, List<String> tcat, List<String> xref, List<String> disc,
                    List<String> acct) throws IOException {
        Path d = Files.createDirectories(tmp.resolve(name));
        if (tcat != null) Files.write(d.resolve("TCATBALF.txt"), tcat);
        if (xref != null) Files.write(d.resolve("XREFFILE.txt"), xref);
        if (disc != null) Files.write(d.resolve("DISCGRP.txt"), disc);
        if (acct != null) Files.write(d.resolve("ACCTFILE.txt"), acct);
        return d;
    }

    static Run run(Path in, Path out) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8);
        PrintStream err = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        int rc;
        try {
            rc = Main.run("2022071800", in, out, TS, ps);
        } finally {
            System.setErr(err);
        }
        return new Run(rc, buf.toString(StandardCharsets.UTF_8), out);
    }
}
