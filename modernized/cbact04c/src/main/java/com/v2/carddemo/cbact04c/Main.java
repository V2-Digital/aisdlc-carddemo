package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** CLI: --in DIR --out DIR [--parm 2022071800] [--clock YYYYMMDDHHMMSSss]. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        String in = null;
        String out = null;
        String parm = "2022071800";
        String clock = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
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
        if (clock == null) {
            ZonedDateTime n = ZonedDateTime.now(ZoneOffset.UTC);
            clock = String.format("%04d%02d%02d%02d%02d%02d%02d", n.getYear(), n.getMonthValue(),
                    n.getDayOfMonth(), n.getHour(), n.getMinute(), n.getSecond(), n.getNano() / 10_000_000);
        }
        Cbact04c job = new Cbact04c(parm, clock);
        int rc = job.run(Path.of(in));
        job.writeOutputs(Path.of(out));
        if (rc != 0) {
            System.err.println("CEE3ABD: ABEND U0999");
        }
        System.exit(rc);
    }

    private static void usage() {
        System.err.println("usage: --in DIR --out DIR [--parm P10] [--clock YYYYMMDDHHMMSSss]");
        System.exit(2);
    }
}
