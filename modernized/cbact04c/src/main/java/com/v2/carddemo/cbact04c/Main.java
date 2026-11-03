package com.v2.carddemo.cbact04c;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** CLI: --in DIR --out DIR --parm YYYYMMDDnn [--now YYYYMMDDHHMMSSss]. Exit code = return code. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Path in = null;
        Path out = null;
        String parm = null;
        Clock clock = Clock.systemUTC();
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--in" -> in = Path.of(args[i + 1]);
                case "--out" -> out = Path.of(args[i + 1]);
                case "--parm" -> parm = args[i + 1];
                case "--now" -> clock = Clock.fixed(parseNow(args[i + 1]), ZoneOffset.UTC);
                default -> usage();
            }
        }
        if (in == null || out == null || parm == null) {
            usage();
        }
        Files.createDirectories(out);
        System.exit(new InterestCalc(in, out, parm, clock, System.out).run());
    }

    static java.time.Instant parseNow(String s) {
        if (s.length() != 16) {
            throw new IllegalArgumentException("--now must be YYYYMMDDHHMMSSss");
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return t.toInstant(ZoneOffset.UTC);
    }

    private static void usage() {
        System.err.println("usage: --in <dir> --out <dir> --parm <10 chars> [--now YYYYMMDDHHMMSSss]");
        System.exit(2);
    }
}
