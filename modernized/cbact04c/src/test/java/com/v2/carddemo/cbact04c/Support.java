package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test helpers: record builders and a run wrapper. */
final class Support {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path STOCK = ROOT.resolve("app/data/ASCII");
    static final String CLOCK = "2022071800000000";

    record Result(int rc, String sysout, List<String> transact, List<String> acct) {}

    static String tcat(String acct, String type, String cat, String bal) {
        return FixedFile.pad(acct + type + cat + ZonedDecimal.encode(new BigDecimal(bal), 9, 2), 50);
    }

    static String xref(String card, String acct) {
        return FixedFile.pad(card + "000000001" + acct, 50);
    }

    static String account(String id, String bal, String credit, String debit, String group) {
        StringBuilder sb = new StringBuilder(FixedFile.pad(id + "Y", 12));
        sb.append(ZonedDecimal.encode(new BigDecimal(bal), 10, 2));
        sb.append(FixedFile.pad("", 54));
        sb.append(ZonedDecimal.encode(new BigDecimal(credit), 10, 2));
        sb.append(ZonedDecimal.encode(new BigDecimal(debit), 10, 2));
        sb.append(FixedFile.pad("", 10));
        sb.append(FixedFile.pad(group, 10));
        return FixedFile.pad(sb.toString(), 300);
    }

    static String disc(String group, String type, String cat, String rate) {
        return FixedFile.pad(FixedFile.pad(group, 10) + type + cat + ZonedDecimal.encode(new BigDecimal(rate), 4, 2), 50);
    }

    static Result run(Path dir, List<String> tcat, List<String> xref, List<String> acct, List<String> disc)
            throws IOException {
        Files.createDirectories(dir);
        FixedFile.write(dir.resolve("TCATBALF.in"), tcat);
        FixedFile.write(dir.resolve("XREFFILE.in"), xref);
        FixedFile.write(dir.resolve("ACCTFILE.in"), acct);
        FixedFile.write(dir.resolve("DISCGRP.in"), disc);
        return run(dir.resolve("TCATBALF.in"), dir.resolve("XREFFILE.in"), dir.resolve("ACCTFILE.in"),
                dir.resolve("DISCGRP.in"), dir.resolve("out"));
    }

    static Result run(Path tcat, Path xref, Path acct, Path disc, Path out) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int rc = new Cbact04c(Main.fixedClock(CLOCK), new PrintStream(bos, true, StandardCharsets.ISO_8859_1))
                .run("2022071800", tcat, xref, acct, disc, out);
        return new Result(rc, bos.toString(StandardCharsets.ISO_8859_1),
                FixedFile.read(out.resolve("TRANSACT.txt"), 350), FixedFile.read(out.resolve("ACCTFILE.txt"), 300));
    }

    static List<String> lines(String s) {
        return List.of(s.split("\n"));
    }
}
