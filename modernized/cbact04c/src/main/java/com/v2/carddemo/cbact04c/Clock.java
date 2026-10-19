package com.v2.carddemo.cbact04c;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Source of FUNCTION CURRENT-DATE: 16-digit YYYYMMDDHHMMSSss setting, else the UTC system clock. */
public final class Clock {
    private final String digits; // YYYYMMDDHHMMSSss

    private Clock(String digits) {
        this.digits = digits;
    }

    /** @param cobCurrentDate value of COB_CURRENT_DATE, or null for the system clock */
    public static Clock of(String cobCurrentDate) {
        if (cobCurrentDate == null) {
            LocalDateTime t = LocalDateTime.now(ZoneOffset.UTC);
            return new Clock(String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(),
                    t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000));
        }
        if (!cobCurrentDate.matches("\\d{16}")) {
            throw new IllegalArgumentException("COB_CURRENT_DATE must be 16 digits YYYYMMDDHHMMSSss: " + cobCurrentDate);
        }
        return new Clock(cobCurrentDate);
    }

    /** DB2-format timestamp YYYY-MM-DD-HH.MM.SS.hh0000 (Z-GET-DB2-FORMAT-TIMESTAMP, :613). */
    public String db2Timestamp() {
        String d = digits;
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8) + "-"
                + d.substring(8, 10) + "." + d.substring(10, 12) + "." + d.substring(12, 14) + "."
                + d.substring(14, 16) + "0000";
    }
}
