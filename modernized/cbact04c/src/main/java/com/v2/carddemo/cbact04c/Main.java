package com.v2.carddemo.cbact04c;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** CLI: {@code java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>}; exit code is the RC. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: cbact04c <PARM> <IN_DIR> <OUT_DIR>");
            System.exit(2);
        }
        Path out = Path.of(args[2]);
        Files.createDirectories(out);
        int rc = new InterestCalc(clock(System.getenv("CARDDEMO_CURRENT_DATE")), System.out)
                .run(args[0], Path.of(args[1]), out);
        System.out.flush();
        System.exit(rc);
    }

    /** {@code YYYYMMDDHHMMSSss} (as the harness), or the system clock when unset. */
    static Clock clock(String env) {
        if (env == null || env.length() < 16) {
            return Clock.system(ZoneOffset.UTC);
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(env.substring(0, 4)),
                Integer.parseInt(env.substring(4, 6)), Integer.parseInt(env.substring(6, 8)),
                Integer.parseInt(env.substring(8, 10)), Integer.parseInt(env.substring(10, 12)),
                Integer.parseInt(env.substring(12, 14)), Integer.parseInt(env.substring(14, 16)) * 10_000_000);
        Instant i = t.toInstant(ZoneOffset.UTC);
        return Clock.fixed(i, ZoneOffset.UTC);
    }
}
