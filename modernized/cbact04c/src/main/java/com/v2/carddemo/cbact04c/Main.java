package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/** CLI: {@code cbact04c <PARM> --TCATBALF=f --XREFFILE=f --ACCTFILE=f --DISCGRP=f --out=dir [--clock=YYYYMMDDHHMMSSss]}. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            return;
        }
        String parm = args[0];
        Map<String, String> opts = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            int eq = a.indexOf('=');
            if (!a.startsWith("--") || eq < 0) {
                usage();
                return;
            }
            opts.put(a.substring(2, eq), a.substring(eq + 1));
        }
        for (String k : new String[] {"TCATBALF", "XREFFILE", "ACCTFILE", "DISCGRP", "out"}) {
            if (!opts.containsKey(k)) {
                usage();
                return;
            }
        }
        String clockSpec = opts.getOrDefault("clock", System.getenv("CARDDEMO_CURRENT_DATE"));
        Clock clock = clockSpec == null || clockSpec.isBlank() ? Clock.systemUTC() : fixedClock(clockSpec);
        PrintStream out = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), false,
                StandardCharsets.ISO_8859_1);
        int rc = new Cbact04c(clock, out).run(parm, Path.of(opts.get("TCATBALF")), Path.of(opts.get("XREFFILE")),
                Path.of(opts.get("ACCTFILE")), Path.of(opts.get("DISCGRP")), Path.of(opts.get("out")));
        out.flush();
        System.exit(rc);
    }

    /** Parses {@code YYYYMMDDHHMMSSss} (same form as COB_CURRENT_DATE). */
    public static Clock fixedClock(String s) {
        if (!s.matches("\\d{16}")) {
            throw new IllegalArgumentException("clock must be YYYYMMDDHHMMSSss: " + s);
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    private static void usage() {
        System.err.println("usage: cbact04c <PARM> --TCATBALF=f --XREFFILE=f --ACCTFILE=f --DISCGRP=f --out=dir"
                + " [--clock=YYYYMMDDHHMMSSss]");
        System.exit(2);
    }
}
