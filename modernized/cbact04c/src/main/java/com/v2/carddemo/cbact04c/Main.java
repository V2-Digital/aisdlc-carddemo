package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** {@code --parm 2022071800 --in <dir> --out <dir> [--now YYYYMMDDHHMMSSss]} */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        String parm = null;
        String in = null;
        String out = null;
        String now = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--parm" -> parm = args[i + 1];
                case "--in" -> in = args[i + 1];
                case "--out" -> out = args[i + 1];
                case "--now" -> now = args[i + 1];
                default -> usage();
            }
        }
        if (parm == null || in == null || out == null) {
            usage();
        }
        if (now == null) {
            LocalDateTime t = LocalDateTime.now(ZoneOffset.UTC);
            now = t.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%02d", t.getNano() / 10_000_000);
        }
        InterestCalc job = new InterestCalc(parm, now);
        int rc = job.run(Path.of(in), Path.of(out));
        job.sysout().forEach(System.out::println);
        System.exit(rc);
    }

    private static void usage() {
        System.err.println("usage: --parm <10 chars> --in <dir> --out <dir> [--now YYYYMMDDHHMMSSss]");
        System.exit(2);
    }
}
