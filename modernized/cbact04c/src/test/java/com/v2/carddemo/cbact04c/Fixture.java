package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** In-memory fixture builder and runner for unit tests. */
final class Fixture {
    final Path dir;
    final List<String> tcat = new ArrayList<>();
    final List<String> xref = new ArrayList<>();
    final List<String> disc = new ArrayList<>();
    final List<String> acct = new ArrayList<>();
    String sysout;
    int rc;

    Fixture(Path dir) { this.dir = dir; }

    static String tcat(String acct, String type, String cat, String bal) {
        return Zoned.right(Zoned.pad(acct, 11, '0', true) + type + cat + Zoned.encode(new BigDecimal(bal), 11), 50);
    }

    static String xref(String card, String acct) {
        return Zoned.right(card + "         " + Zoned.pad(acct, 11, '0', true), 50);
    }

    static String disc(String group, String type, String cat, String rate) {
        return Zoned.right(Zoned.right(group, 10) + type + cat + Zoned.encode(new BigDecimal(rate), 6), 50);
    }

    static String acct(String id, String bal, String group) {
        StringBuilder sb = new StringBuilder(Zoned.right(Zoned.pad(id, 11, '0', true) + "Y" + Zoned.encode(new BigDecimal(bal), 12), 300));
        sb.replace(78, 90, Zoned.encode(new BigDecimal("5.00"), 12));
        sb.replace(90, 102, Zoned.encode(new BigDecimal("7.00"), 12));
        sb.replace(112, 122, Zoned.right(group, 10));
        return sb.toString();
    }

    Path file(String name) { return dir.resolve(name + ".txt"); }

    int run() throws IOException {
        FileIo.write(file("TCATBALF"), tcat);
        FileIo.write(file("XREFFILE"), xref);
        FileIo.write(file("DISCGRP"), disc);
        FileIo.write(file("ACCTFILE"), acct);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        rc = Main.run(new String[] {"--parm", "2022071800", "--clock", "2022071800000000",
                "--TCATBALF", file("TCATBALF").toString(), "--XREFFILE", file("XREFFILE").toString(),
                "--DISCGRP", file("DISCGRP").toString(), "--ACCTFILE", file("ACCTFILE").toString(),
                "--TRANSACT", file("TRANSACT").toString()}, null,
                new PrintStream(buf, true, StandardCharsets.ISO_8859_1));
        sysout = buf.toString(StandardCharsets.ISO_8859_1);
        return rc;
    }

    List<String> lines(String dd) throws IOException {
        return Files.readAllLines(file(dd), StandardCharsets.ISO_8859_1);
    }

    /** Two accounts so the first is rewritten; account 1 gets the interesting category. */
    static Fixture two(Path dir, String bal, String rate, String group) {
        Fixture f = new Fixture(dir);
        f.tcat.add(tcat("1", "01", "0001", bal));
        f.tcat.add(tcat("2", "01", "0001", "100.00"));
        f.xref.add(xref("1111111111111111", "1"));
        f.xref.add(xref("2222222222222222", "2"));
        f.acct.add(acct("1", "1000.00", group));
        f.acct.add(acct("2", "1000.00", group));
        f.disc.add(disc(group.isBlank() ? "DEFAULT" : group, "01", "0001", rate));
        f.disc.add(disc("DEFAULT", "01", "0001", rate));
        return f;
    }
}
