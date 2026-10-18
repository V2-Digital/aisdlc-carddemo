package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Entry point: {@code java -jar cbact04c.jar <PARM>}; files bound by DD_* env vars. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        String parm = args.length > 0 ? args[0] : "";
        String now = System.getenv("CARDDEMO_CURRENT_DATE");
        Clock clock = Clock.systemUTC();
        if (now != null && !now.isEmpty()) {
            // YYYYMMDDHHMMSSss, as the harness
            LocalDateTime t = LocalDateTime.parse(now.substring(0, 14), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    .plusNanos(Long.parseLong(now.substring(14)) * 10_000_000L);
            clock = Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
        int rc = new Cbact04c(dd("TCATBALF"), dd("XREFFILE"), dd("ACCTFILE"), dd("DISCGRP"), dd("TRANSACT"),
                clock, System.out, System.err).run(parm);
        System.exit(rc);
    }

    private static Path dd(String name) {
        String v = System.getenv("DD_" + name);
        return Path.of(v == null ? name + ".txt" : v);
    }
}
