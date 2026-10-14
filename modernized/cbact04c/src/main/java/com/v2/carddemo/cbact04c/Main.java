package com.v2.carddemo.cbact04c;

import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: cbact04c <PARM> <IN_DIR> <OUT_DIR>");
            System.exit(2);
        }
        String env = System.getenv("CARDDEMO_CURRENT_DATE");
        String now = env != null && env.length() >= 16 ? env : "2022071800000000";
        int rc = new InterestCalc(args[0], () -> now, System.out).run(Path.of(args[1]), Path.of(args[2]));
        System.out.flush();
        System.exit(rc);
    }
}
