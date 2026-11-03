package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/** CLI: --data DIR [--input-dir DIR] --out DIR --parm YYYYMMDDHH [--clock YYYYMMDDHHMMSSss]. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    public static int run(String[] args) throws IOException {
        Map<String, String> opt = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            opt.put(args[i], args[i + 1]);
        }
        if (!opt.containsKey("--data") || !opt.containsKey("--out") || !opt.containsKey("--parm")) {
            System.err.println("usage: --data <dir> [--input-dir <dir>] --out <dir> --parm <PARM> [--clock YYYYMMDDHHMMSSss]");
            return 2;
        }
        Clock clock = opt.containsKey("--clock") ? fixed(opt.get("--clock")) : Clock.systemUTC();
        Path inputDir = opt.containsKey("--input-dir") ? Path.of(opt.get("--input-dir")) : null;
        InterestCalc calc = new InterestCalc(Path.of(opt.get("--data")), inputDir, opt.get("--parm"), clock);
        int rc = calc.run(Path.of(opt.get("--out")));
        calc.sysout().forEach(System.out::println);
        if (rc != 0) {
            System.err.println("CEE3ABD: ABEND U0999");
        }
        return rc;
    }

    static Clock fixed(String s) {
        LocalDateTime t = LocalDateTime.of(
                Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
                Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
                Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
                Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
