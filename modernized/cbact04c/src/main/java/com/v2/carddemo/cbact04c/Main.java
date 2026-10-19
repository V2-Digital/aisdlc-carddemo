package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** CLI: {@code java -jar cbact04c.jar <PARM-DATE> <IN_DIR> <OUT_DIR>}. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: cbact04c <PARM-DATE> <IN_DIR> <OUT_DIR>");
            System.exit(2);
        }
        int rc = new Cbact04c(clock(System.getenv("CARDDEMO_CURRENT_DATE")), System.out)
                .run(args[0], Path.of(args[1]), Path.of(args[2]));
        System.out.flush();
        System.exit(rc);
    }

    /** Parses YYYYMMDDHHMMSSss (hundredths) as a fixed UTC clock; null means the system clock. */
    static Clock clock(String s) {
        if (s == null || s.isBlank()) {
            return Clock.systemUTC();
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)),
                Integer.parseInt(s.substring(4, 6)), Integer.parseInt(s.substring(6, 8)),
                Integer.parseInt(s.substring(8, 10)), Integer.parseInt(s.substring(10, 12)),
                Integer.parseInt(s.substring(12, 14)), Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
