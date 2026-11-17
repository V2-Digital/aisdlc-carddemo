package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Usage: {@code java -jar cbact04c.jar <PARM>}. Datasets are bound by env vars named like the JCL DDs:
 * DD_TCATBALF, DD_XREFFILE, DD_DISCGRP, DD_ACCTFILE (read; ACCTFILE is rewritten in place) and DD_TRANSACT
 * (output). CARDDEMO_CURRENT_DATE (YYYYMMDDHHMMSSss, UTC) freezes the clock.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        System.exit(run(args.length > 0 ? args[0] : "", System.getenv(), System.out, System.err));
    }

    static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) throws IOException {
        return run(args.length > 0 ? args[0] : "", env, out, err);
    }

    static int run(String parm, Map<String, String> env, PrintStream out, PrintStream err) throws IOException {
        Map<String, Path> in = new HashMap<>();
        for (String dd : new String[] {"TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE"}) {
            String p = env.get("DD_" + dd);
            if (p != null) {
                in.put(dd, Path.of(p));
            }
        }
        InterestCalc calc = new InterestCalc(in, parm, clock(env.get("CARDDEMO_CURRENT_DATE")), out::println);
        int rc = 0;
        try {
            calc.run();
        } catch (Abend a) {
            err.println("CEE3ABD: ABEND " + a.getMessage());
            rc = 16;
        }
        String transact = env.get("DD_TRANSACT");
        if (transact != null) {
            Datasets.write(Path.of(transact), calc.transact());
        }
        if (in.containsKey("ACCTFILE") && !calc.accountFile().isEmpty()) {
            Datasets.write(in.get("ACCTFILE"), calc.accountFile());
        }
        return rc;
    }

    static Clock clock(String s) {
        if (s == null || s.length() != 16) {
            return Clock.systemUTC();
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
