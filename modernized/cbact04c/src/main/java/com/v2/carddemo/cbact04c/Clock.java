package com.v2.carddemo.cbact04c;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

/** Source of the DB2-style timestamp {@code YYYY-MM-DD-HH.MM.SS.hh0000}. */
final class Clock {
    private Clock() {
    }

    /** CARDDEMO_CURRENT_DATE (YYYYMMDDHHMMSSss) freezes the clock; otherwise system UTC. */
    static Supplier<String> fromEnv() {
        String frozen = System.getenv("CARDDEMO_CURRENT_DATE");
        if (frozen != null && frozen.length() >= 16) {
            return fixed(frozen);
        }
        return () -> {
            ZonedDateTime t = ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
            return format(String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(),
                    t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000));
        };
    }

    static Supplier<String> fixed(String yyyymmddhhmmssss) {
        String ts = format(yyyymmddhhmmssss);
        return () -> ts;
    }

    private static String format(String d) {
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8) + "-"
                + d.substring(8, 10) + "." + d.substring(10, 12) + "." + d.substring(12, 14) + "."
                + d.substring(14, 16) + "0000";
    }
}
