package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code Main <PARM> [--TCATBALF=p] [--XREFFILE=p] [--DISCGRP=p] [--ACCTFILE=in]
 * [--ACCTFILE-OUT=p] [--TRANSACT=p] [--clock=YYYYMMDDHHMMSSss]}.
 * Missing paths fall back to env {@code DD_<DD>}. SYSOUT goes to stdout; abend exits 16.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        System.exit(run(args, System.getenv(), new PrintStream(System.out, false, StandardCharsets.ISO_8859_1),
                System.err));
    }

    static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) throws IOException {
        String parm = "";
        Map<String, String> opts = new HashMap<>();
        for (String a : args) {
            if (a.startsWith("--") && a.contains("=")) {
                opts.put(a.substring(2, a.indexOf('=')), a.substring(a.indexOf('=') + 1));
            } else {
                parm = a;
            }
        }
        Path acctIn = path(opts, env, "ACCTFILE");
        Path acctOut = opts.containsKey("ACCTFILE-OUT") ? Path.of(opts.get("ACCTFILE-OUT")) : acctIn;
        Path transact = path(opts, env, "TRANSACT");
        String fixed = opts.getOrDefault("clock", env.get("CARDDEMO_CURRENT_DATE"));
        Supplier<String> clock = fixed != null ? () -> fixed : Main::systemClock;

        InterestCalc calc = null;
        try {
            calc = new InterestCalc(parm, clock, out::println,
                    Records.read(path(opts, env, "TCATBALF"), Records.TCATBAL_LEN, "TCATBALF"),
                    Records.read(path(opts, env, "XREFFILE"), Records.XREF_LEN, "XREFFILE"),
                    Records.read(path(opts, env, "DISCGRP"), Records.DISCGRP_LEN, "DISCGRP"),
                    Records.read(acctIn, Records.ACCT_LEN, "ACCTFILE"));
            calc.run();
            return 0;
        } catch (Abend e) {
            out.flush();
            err.println(e.getMessage());
            return Abend.RC;
        } finally {
            out.flush();
            if (calc != null && transact != null && acctOut != null) {
                Records.write(transact, calc.transactions());
                Records.write(acctOut, calc.accounts());
            }
        }
    }

    private static Path path(Map<String, String> opts, Map<String, String> env, String dd) {
        String p = opts.getOrDefault(dd, env.get("DD_" + dd));
        return p == null ? null : Path.of(p);
    }

    private static String systemClock() {
        LocalDateTime t = LocalDateTime.now();
        return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }
}
