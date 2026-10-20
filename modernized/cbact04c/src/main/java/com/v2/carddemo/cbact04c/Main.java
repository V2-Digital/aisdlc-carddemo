package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** CLI: {@code --parm <date10> --out <dir> [--in <dir>] [--dd DD=path]...}. Exit code = RC. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        String parm = null;
        Path in = null;
        Path out = null;
        Map<String, Path> dd = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--parm" -> parm = args[++i];
                case "--in" -> in = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--dd" -> {
                    String[] kv = args[++i].split("=", 2);
                    dd.put(kv[0], Path.of(kv[1]));
                }
                default -> usage();
            }
        }
        if (parm == null || out == null) {
            usage();
        }
        for (String name : new String[] {"TCATBALF", "XREFFILE", "ACCTFILE", "DISCGRP"}) {
            if (!dd.containsKey(name) && in != null) {
                dd.put(name, in.resolve(name + ".txt"));
            }
        }
        String frozen = System.getenv("CARDDEMO_CURRENT_DATE");
        Supplier<String> clock = frozen != null && !frozen.isEmpty() ? () -> frozen : Main::systemClock;
        System.exit(Cbact04c.run(dd, out, parm, clock));
    }

    private static String systemClock() {
        LocalDateTime t = LocalDateTime.now();
        return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    private static void usage() {
        System.err.println("usage: --parm <YYYYMMDD00> --out <dir> [--in <dir>] [--dd DD=path]...");
        System.exit(2);
    }
}
