package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** CLI: java -jar cbact04c.jar --PARM=... --TCATBALF=... --XREFFILE=... --DISCGRP=... --ACCTFILE=... --TRANSACT=... */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, new PrintStream(System.out, false, StandardCharsets.ISO_8859_1), System.err));
    }

    /** Returns the exit code: 0 on success, 16 on abend or input error. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        Map<String, String> opt = new HashMap<>();
        for (String a : args) {
            int eq = a.indexOf('=');
            if (!a.startsWith("--") || eq < 0) {
                err.println("bad argument: " + a);
                return 16;
            }
            opt.put(a.substring(2, eq), a.substring(eq + 1));
        }
        for (String req : List.of("TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE", "TRANSACT")) {
            if (!opt.containsKey(req)) {
                err.println("missing argument --" + req);
                return 16;
            }
        }
        Clock clock = clock(opt.get("CURRENT-DATE"));
        InterestCalc.Inputs in;
        try {
            in = load(opt);
        } catch (IOException | IllegalArgumentException e) {
            out.println("INPUT ERROR: " + e.getMessage());
            out.flush();
            return 16;
        }
        InterestCalc.Result r;
        try {
            r = new InterestCalc(out, clock).run(in, opt.getOrDefault("PARM", ""));
        } catch (Abend e) {
            err.println("CEE3ABD: ABEND U0999");
            out.flush();
            return 16;
        }
        out.flush();
        try {
            // D1: outputs are written only after a clean run.
            FixedFile.write(Path.of(opt.get("TRANSACT")), r.transactions());
            Path acctOut = Path.of(opt.getOrDefault("ACCTFILE-OUT", opt.get("ACCTFILE")));
            FixedFile.write(acctOut, List.copyOf(r.accounts().values()));
        } catch (IOException e) {
            out.println("OUTPUT ERROR: " + e.getMessage());
            out.flush();
            return 16;
        }
        return 0;
    }

    private static Clock clock(String currentDate) {
        if (currentDate == null) {
            return Clock.systemUTC();
        }
        String d = currentDate;
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(d.substring(0, 4)), Integer.parseInt(d.substring(4, 6)),
                Integer.parseInt(d.substring(6, 8)), Integer.parseInt(d.substring(8, 10)),
                Integer.parseInt(d.substring(10, 12)), Integer.parseInt(d.substring(12, 14)),
                Integer.parseInt(d.substring(14, 16)) * 10_000_000);
        Instant i = t.toInstant(ZoneOffset.UTC);
        return Clock.fixed(i, ZoneOffset.UTC);
    }

    private static InterestCalc.Inputs load(Map<String, String> opt) throws IOException {
        List<String> tcat = readIfPresent(opt.get("TCATBALF"), 50);
        List<String> xref = readIfPresent(opt.get("XREFFILE"), 50);
        List<String> disc = readIfPresent(opt.get("DISCGRP"), 50);
        List<String> acct = readIfPresent(opt.get("ACCTFILE"), 300);
        Map<String, String> xrefByAcct = null;
        if (xref != null) {
            xrefByAcct = new TreeMap<>();
            Map<String, String> primary = new HashMap<>();
            for (String x : xref) {
                dup(primary.put(x.substring(0, 16), x), "XREFFILE", x.substring(0, 16));
                dup(xrefByAcct.put(x.substring(25, 36), x.substring(0, 16)), "XREFFILE", x.substring(25, 36));
            }
        }
        return new InterestCalc.Inputs(tcat, xrefByAcct, keyed(disc, 16, "DISCGRP"), keyed(acct, 11, "ACCTFILE"));
    }

    private static Map<String, String> keyed(List<String> recs, int keyLen, String dd) {
        if (recs == null) {
            return null;
        }
        Map<String, String> m = new TreeMap<>();
        for (String r : recs) {
            dup(m.put(r.substring(0, keyLen), r), dd, r.substring(0, keyLen));
        }
        return m;
    }

    private static void dup(Object previous, String dd, String key) {
        if (previous != null) {
            throw new IllegalArgumentException(dd + " duplicate key " + key);
        }
    }

    private static List<String> readIfPresent(String path, int lrecl) throws IOException {
        Path p = Path.of(path);
        return Files.exists(p) ? FixedFile.read(p, lrecl) : null;
    }
}
