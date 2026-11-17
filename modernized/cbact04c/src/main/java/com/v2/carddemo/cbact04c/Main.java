package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** CLI: {@code java -jar cbact04c.jar <PARM>}; datasets via DD_&lt;ddname&gt; env vars. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        String parm = args.length > 0 ? args[0] : "";
        Config cfg = new Config(parm, dd("TCATBALF"), dd("XREFFILE"), dd("DISCGRP"), dd("ACCTFILE"),
                dd("TRANSACT"), clock(System.getenv("CARDDEMO_CURRENT_DATE")), System.out, System.err);
        int rc = Cbact04c.run(cfg);
        System.out.flush();
        System.exit(rc);
    }

    private static Path dd(String name) {
        String v = System.getenv("DD_" + name);
        return v == null ? null : Path.of(v);
    }

    /** Parses YYYYMMDDHHMMSSss (hundredths) into a fixed clock; null means the system clock in UTC. */
    static Clock clock(String s) {
        if (s == null || s.isBlank()) {
            return Clock.system(ZoneOffset.UTC);
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        Instant i = t.toInstant(ZoneOffset.UTC);
        return Clock.fixed(i, ZoneOffset.UTC);
    }
}
