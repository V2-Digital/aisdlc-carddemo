package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TreeMap;
import java.util.function.Supplier;

/** CLI: --parm 2022071800 --in DIR --out DIR [--current-date YYYYMMDDHHMMSSss] */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String parm = null;
        String in = null;
        String out = null;
        String fixed = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--parm" -> parm = args[i + 1];
                case "--in" -> in = args[i + 1];
                case "--out" -> out = args[i + 1];
                case "--current-date" -> fixed = args[i + 1];
                default -> usage();
            }
        }
        if (parm == null || in == null || out == null || args.length % 2 != 0) {
            usage();
        }
        System.exit(run(parm, Path.of(in), Path.of(out), fixed, System.out));
    }

    private static void usage() {
        System.err.println("usage: --parm YYYYMMDDNN --in <dir> --out <dir> [--current-date YYYYMMDDHHMMSSss]");
        System.exit(2);
    }

    /** Runs the program and writes TRANSACT.txt and ACCTFILE.txt into {@code out}; returns the RC. */
    public static int run(String parm, Path in, Path out, String fixedDate, PrintStream sysout)
            throws IOException {
        TreeMap<String, String> tcat = Datasets.load(in, "TCATBALF", Records.TCATBAL_LEN, 17);
        TreeMap<String, String> xref = Datasets.load(in, "XREFFILE", Records.XREF_LEN, 16);
        TreeMap<String, String> disc = Datasets.load(in, "DISCGRP", Records.DISC_LEN, 16);
        TreeMap<String, String> acct = Datasets.load(in, "ACCTFILE", Records.ACCT_LEN, 11);
        Supplier<String> clock = fixedDate != null ? () -> fixedDate : Main::systemClock;
        Cbact04c.Result r = new Cbact04c(parm, clock, sysout, tcat, xref, disc, acct).run();
        sysout.flush();
        Files.createDirectories(out);
        Datasets.write(out.resolve("TRANSACT.txt"), r.transact());
        if (r.accounts() != null) {
            Datasets.write(out.resolve("ACCTFILE.txt"), r.accounts());
        }
        return r.rc();
    }

    /** Same 16-digit form as COB_CURRENT_DATE, UTC. */
    static String systemClock() {
        ZonedDateTime t = ZonedDateTime.now(ZoneOffset.UTC);
        return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }
}
