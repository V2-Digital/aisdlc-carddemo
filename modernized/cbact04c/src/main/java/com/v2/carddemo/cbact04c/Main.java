package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** CLI: java -jar cbact04c.jar --parm P --tcatbalf F --xreffile F --discgrp F --acctfile-in F --acctfile-out F --transact F [--sysout F] */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i], args[i + 1]);
        for (String k : new String[] {"--parm", "--tcatbalf", "--xreffile", "--discgrp", "--acctfile-in",
                "--acctfile-out", "--transact"}) {
            if (!a.containsKey(k)) {
                System.err.println("missing argument " + k);
                System.exit(2);
            }
        }
        Clock clock;
        try {
            clock = Clock.of(System.getenv("COB_CURRENT_DATE"));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(Cbact04c.EXIT_ABEND);
            return;
        }
        Sysout sysout = new Sysout(System.out);
        Cbact04c.Files_ f = new Cbact04c.Files_(Path.of(a.get("--tcatbalf")), Path.of(a.get("--xreffile")),
                Path.of(a.get("--discgrp")), Path.of(a.get("--acctfile-in")), Path.of(a.get("--acctfile-out")),
                Path.of(a.get("--transact")));
        int rc = new Cbact04c(f, a.get("--parm"), clock, sysout).run();
        if (a.containsKey("--sysout")) sysout.writeTo(Path.of(a.get("--sysout")));
        if (rc == Cbact04c.EXIT_ABEND) System.err.println("CEE3ABD: ABEND U0999");
        System.exit(rc);
    }
}
