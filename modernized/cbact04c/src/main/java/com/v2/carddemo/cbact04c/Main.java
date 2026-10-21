package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Entry point: {@code java -jar cbact04c.jar <PARM>} with DD_&lt;ddname&gt; env vars and CARDDEMO_CURRENT_DATE. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        String parm = args.length > 0 ? args[0] : "";
        Map<String, Path> dd = new HashMap<>();
        for (String name : new String[] {"TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE", "TRANSACT"}) {
            String path = System.getenv("DD_" + name);
            if (path != null && !path.isEmpty()) {
                dd.put(name, Path.of(path));
            }
        }
        String fixed = System.getenv("CARDDEMO_CURRENT_DATE");
        Supplier<String> clock = fixed != null && fixed.matches("\\d{16}") ? () -> fixed : Main::systemClock;
        PrintStream out = new PrintStream(System.out, false, StandardCharsets.ISO_8859_1);
        System.exit(new Cbact04c(dd, parm, clock, out).run());
    }

    private static String systemClock() {
        ZonedDateTime t = ZonedDateTime.now(ZoneOffset.UTC);
        return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }
}
