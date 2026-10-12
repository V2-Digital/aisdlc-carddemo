package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** CLI for CBACT04C (INTCALC). See modernized/cbact04c/README.md. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Returns the exit status: 0 ok, 16 abend, 3 load error, 2 usage. */
    public static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        Map<String, String> o = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) return usage(stderr);
            o.put(args[i].substring(2), args[i + 1]);
        }
        for (String k : new String[] {"parm", "tcatbalf", "xreffile", "discgrp", "acctfile", "transact"}) {
            if (!o.containsKey(k)) return usage(stderr);
        }
        String clockArg = o.getOrDefault("clock", System.getenv("COB_CURRENT_DATE"));
        if (clockArg != null && !clockArg.matches("\\d{16}")) return usage(stderr);
        Supplier<String> clock = clockArg != null ? () -> clockArg : Main::systemClock;

        Path acct = Path.of(o.get("acctfile"));
        InterestCalc.Files_ f = new InterestCalc.Files_(Path.of(o.get("tcatbalf")), Path.of(o.get("xreffile")),
                Path.of(o.get("discgrp")), acct, o.containsKey("acctfile-out") ? Path.of(o.get("acctfile-out")) : acct,
                Path.of(o.get("transact")));
        PrintStream sysout = new PrintStream(stdout, false, StandardCharsets.ISO_8859_1);
        try {
            return new InterestCalc(f, o.get("parm"), clock, sysout).run();
        } catch (Abend a) {
            sysout.flush();
            stderr.println(a.getMessage());
            return 16;
        } catch (Ksds.LoadError e) {
            stderr.println("LOAD ERROR: " + e.getMessage());
            return 3;
        } catch (java.io.IOException e) {
            stderr.println("I/O error: " + e);
            return 16;
        } finally {
            sysout.flush();
        }
    }

    private static int usage(PrintStream err) {
        err.println("usage: Main --parm P --tcatbalf F --xreffile F --discgrp F --acctfile F --transact F"
                + " [--acctfile-out F] [--clock YYYYMMDDHHMMSSss]");
        return 2;
    }

    private static String systemClock() {
        LocalDateTime t = LocalDateTime.now();
        return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }
}
