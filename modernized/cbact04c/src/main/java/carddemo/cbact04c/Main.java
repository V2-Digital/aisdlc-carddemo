package carddemo.cbact04c;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** CLI: java -jar cbact04c.jar &lt;PARM&gt; &lt;IN_DIR&gt; &lt;OUT_DIR&gt;. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: cbact04c <PARM> <IN_DIR> <OUT_DIR>");
            System.exit(2);
        }
        Clock clock = clockFrom(System.getenv("CARDDEMO_CURRENT_DATE"));
        PrintStream out = new PrintStream(System.out, false, StandardCharsets.ISO_8859_1);
        int rc = new Cbact04c(args[0], clock, out, System.err).run(Path.of(args[1]), Path.of(args[2]));
        out.flush();
        System.exit(rc);
    }

    /** env value is YYYYMMDDHHMMSSss; unset means the system clock. */
    static Clock clockFrom(String v) {
        if (v == null || v.length() < 16) {
            return Clock.systemUTC();
        }
        LocalDateTime t = LocalDateTime.of(
                Integer.parseInt(v.substring(0, 4)), Integer.parseInt(v.substring(4, 6)),
                Integer.parseInt(v.substring(6, 8)), Integer.parseInt(v.substring(8, 10)),
                Integer.parseInt(v.substring(10, 12)), Integer.parseInt(v.substring(12, 14)),
                Integer.parseInt(v.substring(14, 16)) * 10_000_000);
        Instant i = t.toInstant(ZoneOffset.UTC);
        return Clock.fixed(i, ZoneOffset.UTC);
    }
}
