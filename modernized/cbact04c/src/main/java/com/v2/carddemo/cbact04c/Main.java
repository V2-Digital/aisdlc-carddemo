package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        System.exit(run(args));
    }

    static int run(String[] args) throws Exception {
        String parm = "";
        Clock clock = Clock.system();
        Path out = null;
        Map<String, Path> dd = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--parm" -> parm = args[++i];
                case "--clock" -> clock = Clock.fixed(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> {
                    int eq = args[i].indexOf('=');
                    if (eq < 0) return usage();
                    dd.put(args[i].substring(0, eq), Path.of(args[i].substring(eq + 1)));
                }
            }
        }
        if (out == null || !dd.keySet().containsAll(java.util.List.of("TCATBALF", "XREFFILE", "ACCTFILE", "DISCGRP"))) {
            return usage();
        }
        try {
            new InterestBatch(parm, clock, System.out::println)
                    .run(dd.get("TCATBALF"), dd.get("XREFFILE"), dd.get("ACCTFILE"), dd.get("DISCGRP"), out);
            return 0;
        } catch (AbendException e) {
            System.err.println(e.getMessage());
            return 16;
        }
    }

    private static int usage() {
        System.err.println("usage: --parm <10 chars> [--clock YYYYMMDDHHMMSSss] --out <dir> "
                + "TCATBALF=<f> XREFFILE=<f> ACCTFILE=<f> DISCGRP=<f>");
        return 2;
    }
}
