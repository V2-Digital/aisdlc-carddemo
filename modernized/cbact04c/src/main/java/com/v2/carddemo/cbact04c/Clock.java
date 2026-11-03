package com.v2.carddemo.cbact04c;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Source of FUNCTION CURRENT-DATE as the 16-digit YYYYMMDDHHMMSShh form. */
public interface Clock {
    String now16();

    static Clock fixed(String digits16) {
        if (digits16 == null || !digits16.matches("\\d{16}")) {
            throw new IllegalArgumentException("clock must be YYYYMMDDHHMMSShh: " + digits16);
        }
        return () -> digits16;
    }

    static Clock system() {
        return () -> {
            ZonedDateTime t = ZonedDateTime.now(ZoneOffset.UTC);
            return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(),
                    t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        };
    }

    /** {@code --now}, else CARDDEMO_CURRENT_DATE, else COB_CURRENT_DATE, else the UTC system clock. */
    static Clock resolve(String nowArg, java.util.Map<String, String> env) {
        if (nowArg != null) return fixed(nowArg);
        for (String k : new String[] {"CARDDEMO_CURRENT_DATE", "COB_CURRENT_DATE"}) {
            String v = env.get(k);
            if (v != null && !v.isEmpty()) return fixed(v);
        }
        return system();
    }

    /** DB2 format timestamp: YYYY-MM-DD-HH.MM.SS.hh0000. */
    static String db2(String d) {
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8) + "-"
                + d.substring(8, 10) + "." + d.substring(10, 12) + "." + d.substring(12, 14) + "."
                + d.substring(14, 16) + "0000";
    }
}
