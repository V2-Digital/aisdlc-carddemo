package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small in-memory input builder for characterization tests. */
final class Fixture {
    final Path dir;
    String acct = "", tcat = "", xref = "", disc = "";
    final ByteArrayOutputStream sys = new ByteArrayOutputStream();
    String parm = "2022071800";
    Clock clock = Cbact04c.fixedClock("2022071800000000");

    Fixture(Path dir) { this.dir = dir; }

    static String pad(String s, int n) { return FixedFile.pad(s, n); }

    /** Account record with balance (zoned, 12 chars) and cycle fields set to non-zero. */
    static String account(String id, String bal12, String group) {
        return pad(id + "Y" + bal12 + " ".repeat(54) + "000000000100" + "00000000020}"
                + " ".repeat(10) + pad(group, 10), 300);
    }
    static String tcat(String acct, String type, String cat, String bal11) { return pad(acct + type + cat + bal11, 50); }
    static String xref(String card, String acct) { return pad(card + " ".repeat(9) + acct, 50); }
    static String disc(String group, String type, String cat, String rate6) { return pad(pad(group, 10) + type + cat + rate6, 50); }

    int run() throws Exception {
        Map<String, Path> dds = new HashMap<>();
        write(dds, "ACCTFILE", acct); write(dds, "TCATBALF", tcat);
        write(dds, "XREFFILE", xref); write(dds, "DISCGRP", disc);
        dds.put("ACCTFILE_OUT", dir.resolve("ACCTFILE_OUT.txt"));
        dds.put("TRANSACT", dir.resolve("TRANSACT.txt"));
        return new Cbact04c(dds, parm, clock, new PrintStream(sys, true, StandardCharsets.ISO_8859_1)).run();
    }

    private void write(Map<String, Path> dds, String dd, String content) throws Exception {
        Path p = dir.resolve(dd + ".txt");
        Files.writeString(p, content, StandardCharsets.ISO_8859_1);
        dds.put(dd, p);
    }

    String sysout() { return sys.toString(StandardCharsets.ISO_8859_1); }
    List<String> lines(String name, int lrecl) throws Exception { return FixedFile.read(dir.resolve(name), lrecl); }
}
