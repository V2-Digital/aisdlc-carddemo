package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;

/**
 * Usage: {@code java -jar cbact04c.jar <inputDir> <outputDir> <PARM-DATE>}
 * or {@code <PARM-DATE>} alone with {@code DATA_DIR} / {@code OUT_DIR} set.
 * {@code CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss} fixes the clock (default: system UTC).
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        String in = System.getenv("DATA_DIR");
        String out = System.getenv("OUT_DIR");
        String parm;
        if (args.length == 3) {
            in = args[0];
            out = args[1];
            parm = args[2];
        } else if (args.length == 1 && in != null && out != null) {
            parm = args[0];
        } else {
            System.err.println("usage: cbact04c <inputDir> <outputDir> <PARM-DATE>");
            System.exit(2);
            return;
        }
        System.exit(new Cbact04c(Path.of(in), Path.of(out), parm, clock(System.getenv("CARDDEMO_CURRENT_DATE")),
                System.out, System.err).run());
    }

    /** Parse YYYYMMDDHHMMSSss into a fixed UTC clock. */
    static Clock clock(String s) {
        if (s == null || s.isBlank()) return Clock.systemUTC();
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        Instant i = t.toInstant(ZoneOffset.UTC);
        return Clock.fixed(i, ZoneOffset.UTC);
    }
}
