package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Runs the program on a fixture directory and exposes outputs. */
final class Support {
    static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path EDGE = ROOT.resolve("legacy/tests/cbact04c_edge");

    int rc;
    String sysout;
    String err;
    List<String> transact;
    List<String> accounts;

    static Support run(Path in, Path out) throws Exception {
        Support s = new Support();
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        s.rc = new Cbact04c(in, out, "2022071800", Main.clock("2022071800000000"),
                new PrintStream(so, true, "ISO-8859-1"), new PrintStream(se, true, "ISO-8859-1")).run();
        s.sysout = so.toString("ISO-8859-1");
        s.err = se.toString("ISO-8859-1");
        s.transact = Files.readAllLines(out.resolve("TRANSACT.txt"), java.nio.charset.StandardCharsets.ISO_8859_1);
        Path a = out.resolve("ACCTFILE.txt");
        s.accounts = Files.exists(a) ? Files.readAllLines(a, java.nio.charset.StandardCharsets.ISO_8859_1) : List.of();
        return s;
    }

    static String amount(String tx) { return tx.substring(132, 143); }
}
