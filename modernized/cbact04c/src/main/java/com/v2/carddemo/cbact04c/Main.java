package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code java -jar cbact04c.jar <PARM-DATE> --dd TCATBALF=<path> ... [--sysout <path>]} */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: cbact04c <PARM-DATE> --dd DD=path ... [--sysout path]");
            System.exit(2);
        }
        Map<String, Path> dd = new LinkedHashMap<>();
        Path sysoutPath = null;
        boolean inDd = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--dd")) {
                inDd = true;
            } else if (a.equals("--sysout")) {
                sysoutPath = Path.of(args[++i]);
                inDd = false;
            } else if (inDd && a.contains("=")) {
                dd.put(a.substring(0, a.indexOf('=')), Path.of(a.substring(a.indexOf('=') + 1)));
            } else {
                System.err.println("unexpected argument: " + a);
                System.exit(2);
            }
        }
        for (String name : new String[] {"TCATBALF", "XREFFILE", "ACCTFILE", "DISCGRP", "TRANSACT"}) {
            if (!dd.containsKey(name)) {
                System.err.println("missing --dd " + name + "=<path>");
                System.exit(2);
            }
        }
        PrintStream out = sysoutPath == null ? System.out : new PrintStream(Files.newOutputStream(sysoutPath), false, "ISO-8859-1");
        int rc = new InterestCalc(dd, args[0], clock(), out, System.err).run();
        out.flush();
        System.exit(rc);
    }

    /** System clock, or the frozen CARDDEMO_CURRENT_DATE (YYYYMMDDHHMMSSss) when set, as in the legacy harness. */
    private static Clock clock() {
        String f = System.getenv("CARDDEMO_CURRENT_DATE");
        if (f == null || f.length() != 16) {
            return Clock.systemUTC();
        }
        java.time.LocalDateTime t = java.time.LocalDateTime.of(Integer.parseInt(f.substring(0, 4)),
                Integer.parseInt(f.substring(4, 6)), Integer.parseInt(f.substring(6, 8)),
                Integer.parseInt(f.substring(8, 10)), Integer.parseInt(f.substring(10, 12)),
                Integer.parseInt(f.substring(12, 14)), Integer.parseInt(f.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(java.time.ZoneOffset.UTC), java.time.ZoneOffset.UTC);
    }
}
