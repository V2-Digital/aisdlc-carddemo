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

/** CLI: java -jar cbact04c.jar --parm 2022071800 --tcatbalf F --xreffile F --acctfile F --discgrp F --transact OUT --acctfile-out OUT */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), false,
                StandardCharsets.ISO_8859_1);
        int rc = run(args, System.getenv("CARDDEMO_CURRENT_DATE"), out);
        out.flush();
        System.exit(rc);
    }

    /** Returns the process exit code. */
    public static int run(String[] args, String currentDate, PrintStream out) {
        try {
            Map<String, String> a = new HashMap<>();
            for (int i = 0; i + 1 < args.length; i += 2) {
                a.put(args[i], args[i + 1]);
            }
            for (String k : new String[] {"--parm", "--tcatbalf", "--xreffile", "--acctfile", "--discgrp",
                    "--transact", "--acctfile-out"}) {
                if (!a.containsKey(k)) {
                    System.err.println("missing argument " + k);
                    return 2;
                }
            }
            InterestCalc.Files files = new InterestCalc.Files(Path.of(a.get("--tcatbalf")),
                    Path.of(a.get("--xreffile")), Path.of(a.get("--acctfile")), Path.of(a.get("--discgrp")),
                    Path.of(a.get("--transact")), Path.of(a.get("--acctfile-out")));
            new InterestCalc(clockFor(currentDate), out).run(a.get("--parm"), files);
            return 0;
        } catch (AbendException e) {
            out.println("ABENDING PROGRAM");
            return AbendException.EXIT_CODE;
        } catch (java.io.IOException e) {
            System.err.println(e);
            out.println("ABENDING PROGRAM");
            return AbendException.EXIT_CODE;
        }
    }

    /** CARDDEMO_CURRENT_DATE is YYYYMMDDHHMMSSss; unset means the system clock in UTC. */
    static Clock clockFor(String currentDate) {
        if (currentDate == null || currentDate.length() < 16) {
            return Clock.system(ZoneOffset.UTC);
        }
        int hh = Integer.parseInt(currentDate.substring(14, 16));
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(currentDate.substring(0, 4)),
                Integer.parseInt(currentDate.substring(4, 6)), Integer.parseInt(currentDate.substring(6, 8)),
                Integer.parseInt(currentDate.substring(8, 10)), Integer.parseInt(currentDate.substring(10, 12)),
                Integer.parseInt(currentDate.substring(12, 14)), hh * 10_000_000);
        return Clock.fixed(Instant.from(t.atOffset(ZoneOffset.UTC)), ZoneOffset.UTC);
    }
}
