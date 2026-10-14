package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/** CLI: java -jar cbact04c.jar --parm P --TCATBALF f --XREFFILE f --DISCGRP f --ACCTFILE f --TRANSACT f [--clock YYYYMMDDHHMMSSss] */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.getenv("CARDDEMO_CURRENT_DATE"), new PrintStream(System.out, false, StandardCharsets.ISO_8859_1)));
    }

    /** Runs the job and returns the exit code (2 for usage errors). */
    public static int run(String[] args, String envClock, PrintStream sysout) {
        Map<String, String> opt = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            opt.put(args[i], args[i + 1]);
        }
        for (String k : new String[] {"--TCATBALF", "--XREFFILE", "--DISCGRP", "--ACCTFILE", "--TRANSACT"}) {
            if (!opt.containsKey(k)) {
                System.err.println("usage: --parm P --TCATBALF f --XREFFILE f --DISCGRP f --ACCTFILE f --TRANSACT f [--clock YYYYMMDDHHMMSSss]");
                return 2;
            }
        }
        String clockSpec = opt.getOrDefault("--clock", envClock);
        Clock clock = clockSpec == null || clockSpec.isBlank() ? Clock.systemUTC() : fixed(clockSpec);
        InterestCalc.Files5 files = new InterestCalc.Files5(Path.of(opt.get("--TCATBALF")), Path.of(opt.get("--XREFFILE")),
                Path.of(opt.get("--DISCGRP")), Path.of(opt.get("--ACCTFILE")), Path.of(opt.get("--TRANSACT")));
        int rc = new InterestCalc(files, opt.getOrDefault("--parm", ""), clock, sysout).run();
        sysout.flush();
        return rc;
    }

    static Clock fixed(String s) {
        int hh = Integer.parseInt(s.substring(14, 16));
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)), hh * 10_000_000);
        Instant i = t.toInstant(ZoneOffset.UTC);
        return Clock.fixed(i, ZoneOffset.UTC);
    }
}
