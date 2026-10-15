package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** CLI: {@code --in <dir> --out <dir> [--parm 2022071800] [--clock YYYYMMDDHHMMSSss]}. */
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
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if (in == null || out == null) {
            System.err.println("usage: --in <dir> --out <dir> [--parm 2022071800] [--clock YYYYMMDDHHMMSSss]");
            System.exit(2);
        }
        if (clock == null) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            clock = String.format("%04d%02d%02d%02d%02d%02d%02d", now.getYear(), now.getMonthValue(),
                    now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano() / 10_000_000);
        }
        int rc = new InterestCalc(parm, clock, System.out).run(Path.of(in), Path.of(out));
        System.out.flush();
        System.exit(rc);
    }
}
