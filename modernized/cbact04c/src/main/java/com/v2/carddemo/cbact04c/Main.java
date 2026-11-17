package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** CLI: {@code --parm 2022071800 --in DIR --out DIR [--now YYYYMMDDHHMMSSss]}. Exit code = RC. */
public final class Main {
    public static void main(String[] args) throws Exception {
        String parm = "", now = null;
        Path in = null, out = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--parm" -> parm = args[i + 1];
                case "--in" -> in = Path.of(args[i + 1]);
                case "--out" -> out = Path.of(args[i + 1]);
                case "--now" -> now = args[i + 1];
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        if (in == null || out == null) {
            System.err.println("usage: --parm P --in DIR --out DIR [--now YYYYMMDDHHMMSSss]");
            System.exit(2);
        }
        if (now == null) {
            ZonedDateTime t = ZonedDateTime.now(ZoneOffset.UTC);
            now = String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                    t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        }
        Files.createDirectories(out);
        PrintStream stdout = new PrintStream(System.out, false, StandardCharsets.ISO_8859_1);
        int rc = 0;
        try {
            new IntCalc(parm, now, stdout::println).run(in, out);
        } catch (Abend a) {
            System.err.println(a.getMessage());
            rc = 16;
        }
        stdout.flush();
        System.exit(rc);
    }
}
