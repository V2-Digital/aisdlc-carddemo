package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Fixture builders and a runner for synthetic scenarios. */
final class Fx {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), java.time.ZoneOffset.UTC);

    final Path dir;
    final ByteArrayOutputStream sysout = new ByteArrayOutputStream();
    final ByteArrayOutputStream syserr = new ByteArrayOutputStream();

    Fx(Path dir) { this.dir = dir; }

    static String pad(String s, int n) { return String.format("%-" + n + "s", s); }

    static String tcat(String acct, String type, String cat, String bal) { return pad(acct + type + cat + bal, 50); }

    static String xref(String card, String acct) { return pad(card + "000000001" + acct, 36); }

    static String disc(String group, String type, String cat, String rate) { return pad(group + type + cat + rate, 50); }

    static String acct(String id, String bal, String group) {
        StringBuilder sb = new StringBuilder(" ".repeat(300));
        sb.replace(0, 11, id).replace(11, 12, "Y").replace(12, 24, bal)
          .replace(78, 90, "00000012345{").replace(90, 102, "00000054321{").replace(112, 122, pad(group, 10));
        return sb.toString();
    }

    void write(String name, List<String> recs) throws IOException {
        Files.writeString(dir.resolve(name), String.join("\n", recs) + (recs.isEmpty() ? "" : "\n"),
                StandardCharsets.ISO_8859_1);
    }

    String read(String name) throws IOException { return Files.readString(dir.resolve(name), StandardCharsets.ISO_8859_1); }

    int run(String parm) {
        Config c = new Config(parm, dir.resolve("TCATBALF.txt"), dir.resolve("XREFFILE.txt"),
                dir.resolve("DISCGRP.txt"), dir.resolve("ACCTFILE.txt"), dir.resolve("TRANSACT.txt"), CLOCK,
                new PrintStream(sysout, true, StandardCharsets.ISO_8859_1),
                new PrintStream(syserr, true, StandardCharsets.ISO_8859_1));
        return Cbact04c.run(c);
    }

    String out() { return sysout.toString(StandardCharsets.ISO_8859_1); }

    /** Standard single-group fixture: DEFAULT rate 15.00 for 01/0001, 0 for 03/0001. */
    void base(List<String> tcat, List<String> accts) throws IOException {
        write("TCATBALF.txt", tcat);
        write("ACCTFILE.txt", accts);
        write("XREFFILE.txt", List.of(xref("4000000000000001", "00000000001"), xref("4000000000000002", "00000000002")));
        write("DISCGRP.txt", List.of(disc("DEFAULT   ", "01", "0001", "00150{"), disc("DEFAULT   ", "03", "0001", "00000{")));
    }
}
