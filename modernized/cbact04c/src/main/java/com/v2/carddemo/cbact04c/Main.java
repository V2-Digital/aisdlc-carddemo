package com.v2.carddemo.cbact04c;

import java.nio.file.Path;

/** CLI: java -jar cbact04c.jar --parm=2022071800 --in=DIR --out=DIR [--now=YYYYMMDDHHMMSShh] */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        String parm = null, in = null, out = null, now = null;
        for (String a : args) {
            if (a.startsWith("--parm=")) parm = a.substring(7);
            else if (a.startsWith("--in=")) in = a.substring(5);
            else if (a.startsWith("--out=")) out = a.substring(6);
            else if (a.startsWith("--now=")) now = a.substring(6);
            else usage("unknown argument: " + a);
        }
        if (parm == null || in == null || out == null) usage("--parm, --in and --out are required");
        Clock clock = Clock.resolve(now, System.getenv());
        int rc = new Cbact04c(Path.of(in), Path.of(out), parm, clock, System.out, System.err).run();
        System.out.flush();
        System.exit(rc);
    }

    private static void usage(String msg) {
        System.err.println(msg);
        System.err.println("usage: cbact04c --parm=YYYYMMDDnn --in=<dir> --out=<dir> [--now=YYYYMMDDHHMMSShh]");
        System.exit(2);
    }
}
