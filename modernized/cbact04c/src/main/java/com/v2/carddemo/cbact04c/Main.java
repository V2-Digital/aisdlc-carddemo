package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

/** CLI: --in DIR --out DIR [--parm 2022071800] [--clock YYYYMMDDHHMMSSss]. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        String in = null;
        String out = null;
        String parm = "2022071800";
        String clock = System.getenv("COB_CURRENT_DATE");
        if (clock == null) {
            clock = System.getenv("CARDDEMO_CURRENT_DATE");
        }
        for (int i = 0; i < args.length - 1; i += 2) {
            switch (args[i]) {
                case "--in" -> in = args[i + 1];
                case "--out" -> out = args[i + 1];
                case "--parm" -> parm = args[i + 1];
                case "--clock" -> clock = args[i + 1];
                default -> usage();
            }
        }
        if (in == null || out == null) {
            usage();
        }
        final String fixed = clock;
        Supplier<String> supplier = fixed != null ? () -> fixed : Main::systemClock;
        int rc = Cbact04c.run(Path.of(in), Path.of(out), parm, supplier, System.out, System.err);
        System.out.flush();
        System.exit(rc);
    }

    private static String systemClock() {
        ZonedDateTime t = ZonedDateTime.now(ZoneOffset.UTC);
        return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(),
            t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    private static void usage() {
        System.err.println("usage: cbact04c --in <dir> --out <dir> [--parm 2022071800] [--clock YYYYMMDDHHMMSSss]");
        System.exit(2);
    }
}
