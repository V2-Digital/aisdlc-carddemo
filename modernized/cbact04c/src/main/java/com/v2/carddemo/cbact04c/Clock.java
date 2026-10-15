package com.v2.carddemo.cbact04c;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Source of FUNCTION CURRENT-DATE (YYYYMMDDHHMMSShh), injectable for deterministic runs. */
@FunctionalInterface
public interface Clock {
    /** @return 16 characters, YYYYMMDDHHMMSShh */
    String currentDate();

    static Clock fixed(String yyyymmddhhmmssss) {
        if (!yyyymmddhhmmssss.matches("\\d{16}")) {
            throw new IllegalArgumentException("clock must be YYYYMMDDHHMMSSss: " + yyyymmddhhmmssss);
        }
        return () -> yyyymmddhhmmssss;
    }

    static Clock system() {
        return () -> {
            ZonedDateTime t = ZonedDateTime.now(ZoneOffset.UTC);
            return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                    t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        };
    }

    /** DB2 format timestamp: YYYY-MM-DD-HH.MM.SS.hh0000 */
    static String db2Timestamp(String c) {
        return c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-" + c.substring(8, 10) + "."
                + c.substring(10, 12) + "." + c.substring(12, 14) + "." + c.substring(14, 16) + "0000";
    }
}
