package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.Clock;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("usage: java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>");
            System.exit(2);
        }
        System.exit(Cbact04c.run(args[0], Path.of(args[1]), Path.of(args[2]), Clock.systemDefaultZone(),
                System.out, System.err));
    }
}
