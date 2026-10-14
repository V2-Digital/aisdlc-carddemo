package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** CLI: {@code java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>}. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: cbact04c <PARM> <IN_DIR> <OUT_DIR>");
            System.exit(2);
        }
        Path out = Path.of(args[2]);
        Files.createDirectories(out);
        int rc;
        try (OutputStream file = Files.newOutputStream(out.resolve("SYSOUT.txt"));
                PrintStream sysout = new PrintStream(new Tee(System.out, file), false, StandardCharsets.ISO_8859_1)) {
            rc = new InterestCalc(sysout, System.err, clock(System.getenv("CARDDEMO_CURRENT_DATE")))
                    .run(args[0], Path.of(args[1]), out);
            sysout.flush();
        }
        System.exit(rc);
    }

    /** {@code YYYYMMDDHHMMSSss} frozen clock, else the system clock (UTC). */
    static Clock clock(String env) {
        if (env == null || env.length() != 16) {
            return Clock.systemUTC();
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(env.substring(0, 4)),
                Integer.parseInt(env.substring(4, 6)), Integer.parseInt(env.substring(6, 8)),
                Integer.parseInt(env.substring(8, 10)), Integer.parseInt(env.substring(10, 12)),
                Integer.parseInt(env.substring(12, 14)), Integer.parseInt(env.substring(14, 16)) * 10_000_000);
        Instant i = t.toInstant(ZoneOffset.UTC);
        return Clock.fixed(i, ZoneOffset.UTC);
    }

    private static final class Tee extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        Tee(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int c) throws IOException {
            a.write(c);
            b.write(c);
        }

        @Override
        public void flush() throws IOException {
            a.flush();
            b.flush();
        }
    }
}
