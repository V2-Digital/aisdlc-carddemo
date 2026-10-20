package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** CLI: {@code java -jar cbact04c.jar <PARM> --in <dir> --out <dir>}. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String parm = null;
        Path in = null;
        Path out = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--in" -> in = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> parm = args[i];
            }
        }
        if (parm == null || in == null || out == null) {
            System.err.println("usage: cbact04c <PARM> --in <dir> --out <dir>");
            System.exit(2);
        }
        System.exit(new Cbact04c(in, out, parm, clock(System.getenv("CARDDEMO_CURRENT_DATE")),
                System.out, System.err).run());
    }

    /** {@code YYYYMMDDHHMMSSss} (UTC) as in the harness, else the system clock. */
    static Clock clock(String yyyymmddhhmmssss) {
        if (yyyymmddhhmmssss == null || yyyymmddhhmmssss.length() != 16) {
            return Clock.systemUTC();
        }
        String s = yyyymmddhhmmssss;
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
