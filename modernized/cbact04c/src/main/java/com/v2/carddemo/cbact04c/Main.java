package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code java -jar cbact04c.jar <PARM> <OUT_DIR> TCATBALF=<f> XREFFILE=<f> ACCTFILE=<f> DISCGRP=<f>}
 * Exit code is the program's return code (0, or 16 on abend).
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: Main <PARM> <OUT_DIR> TCATBALF=<f> XREFFILE=<f> ACCTFILE=<f> DISCGRP=<f>");
            System.exit(2);
        }
        Map<String, Path> dd = new HashMap<>();
        for (int i = 2; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq < 1) {
                System.err.println("bad argument: " + args[i]);
                System.exit(2);
            }
            dd.put(args[i].substring(0, eq), Path.of(args[i].substring(eq + 1)));
        }
        Path out = Path.of(args[1]);
        Files.createDirectories(out);

        Cbact04c.Inputs in = new Cbact04c.Inputs(read(dd.get("TCATBALF")), read(dd.get("XREFFILE")),
                read(dd.get("ACCTFILE")), read(dd.get("DISCGRP")));
        Cbact04c.Result r = Cbact04c.run(args[0], in, clock());

        for (String f : new String[] {"TRANSACT", "ACCTFILE", "SYSOUT", "RC"}) {
            Files.deleteIfExists(out.resolve(f + ".txt"));
        }
        if (r.rc() == Cbact04c.RC_OK) {
            write(out.resolve("TRANSACT.txt"), r.transact());
            write(out.resolve("ACCTFILE.txt"), r.acctfile());
        } else {
            System.err.println("CEE3ABD: ABEND U0999");
        }
        write(out.resolve("SYSOUT.txt"), r.sysout());
        write(out.resolve("RC.txt"), List.of(Integer.toString(r.rc())));
        System.exit(r.rc());
    }

    private static Supplier<String> clock() {
        String fixed = System.getenv("CARDDEMO_CURRENT_DATE");
        if (fixed != null && fixed.matches("\\d{16}")) return () -> fixed;
        return () -> {
            ZonedDateTime t = ZonedDateTime.now(ZoneOffset.UTC);
            return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(),
                    t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
        };
    }

    /** Lines without CR; {@code null} if the file is absent or unreadable. */
    private static List<String> read(Path p) {
        if (p == null || !Files.isRegularFile(p)) return null;
        try {
            List<String> lines = new ArrayList<>();
            for (String l : Files.readAllLines(p, StandardCharsets.ISO_8859_1)) {
                lines.add(l.endsWith("\r") ? l.substring(0, l.length() - 1) : l);
            }
            return lines;
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(Path p, List<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        Files.writeString(p, sb, StandardCharsets.ISO_8859_1);
    }
}
