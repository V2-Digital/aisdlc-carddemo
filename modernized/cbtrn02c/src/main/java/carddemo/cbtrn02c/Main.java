package carddemo.cbtrn02c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLI: {@code Main [--stock] <IN_DIR> <OUT_DIR>}. With {@code --stock}, IN_DIR is app/data/ASCII
 * (dailytran/cardxref/acctdata/tcatbal.txt); otherwise it holds DD-named files.
 * Clock: env CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss, else system time (UTC).
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    static int run(String[] args) throws IOException {
        boolean stock = args.length == 3 && args[0].equals("--stock");
        int o = stock ? 1 : 0;
        if (args.length != o + 2) {
            System.err.println("usage: Main [--stock] <IN_DIR> <OUT_DIR>");
            return 2;
        }
        Path in = Path.of(args[o]);
        Path out = Path.of(args[o + 1]);
        Files.createDirectories(out);
        try {
            if (stock) {
                Path tmp = Files.createTempDirectory("cbtrn02c-in");
                for (Map.Entry<String, String> e : Map.of("DALYTRAN", "dailytran", "XREFFILE", "cardxref",
                        "ACCTFILE", "acctdata", "TCATBALF", "tcatbal").entrySet()) {
                    Files.copy(in.resolve(e.getValue() + ".txt"), tmp.resolve(e.getKey() + ".txt"));
                }
                in = tmp;
            }
            List<String> lines = new ArrayList<>();
            PrintStream stdout = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
            int rc = new Cbtrn02c(in, out, clock(), l -> {
                lines.add(l);
                stdout.println(l);
            }).run();
            if (rc == 16 && lines.contains("ABENDING PROGRAM")) {
                System.err.println("CEE3ABD: ABEND U0999 TIMING=0");
            }
            KsdsFile.writeLines(out.resolve("SYSOUT.txt"), lines);
            return rc;
        } catch (LoadException e) {
            System.err.println("load error: " + e.getMessage());
            return 16;
        }
    }

    static Clock clock() {
        String d = System.getenv("CARDDEMO_CURRENT_DATE");
        if (d == null || d.length() != 16) {
            return Clock.systemUTC();
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(d.substring(0, 4)), Integer.parseInt(d.substring(4, 6)),
                Integer.parseInt(d.substring(6, 8)), Integer.parseInt(d.substring(8, 10)),
                Integer.parseInt(d.substring(10, 12)), Integer.parseInt(d.substring(12, 14)),
                Integer.parseInt(d.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
