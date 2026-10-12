package com.v2.carddemo.cbtrn02c;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Entry point. DD files from env {@code DD_<NAME>} (default: <NAME> in cwd); clock from CARDDEMO_CURRENT_DATE. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Clock clock = Clock.systemDefaultZone();
        String fixed = System.getenv("CARDDEMO_CURRENT_DATE");
        if (fixed != null && fixed.length() >= 16) {
            LocalDateTime t = LocalDateTime.of(Integer.parseInt(fixed.substring(0, 4)),
                    Integer.parseInt(fixed.substring(4, 6)), Integer.parseInt(fixed.substring(6, 8)),
                    Integer.parseInt(fixed.substring(8, 10)), Integer.parseInt(fixed.substring(10, 12)),
                    Integer.parseInt(fixed.substring(12, 14)), Integer.parseInt(fixed.substring(14, 16)) * 10_000_000);
            clock = Clock.fixed(t.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
        Cbtrn02c program = new Cbtrn02c(clock, System.out, System.err, name -> {
            String p = System.getenv("DD_" + name);
            return Path.of(p != null ? p : name);
        });
        int rc = program.run();
        System.out.flush();
        System.exit(rc);
    }
}
