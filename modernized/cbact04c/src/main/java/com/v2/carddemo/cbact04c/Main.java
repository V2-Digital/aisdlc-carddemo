package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** CLI: {@code cbact04c <PARM-DATE> <OUT_DIR> [INPUT_DIR]}. Exit code 0, or 16 on abend. */
public final class Main {
    private static final Map<String, String> STOCK = Map.of(
            "TCATBALF", "tcatbal.txt", "XREFFILE", "cardxref.txt",
            "ACCTFILE", "acctdata.txt", "DISCGRP", "discgrp.txt");
    private static final Map<String, Integer> LRECL = Map.of(
            "TCATBALF", InterestCalc.TCATBAL_LRECL, "XREFFILE", InterestCalc.XREF_LRECL,
            "ACCTFILE", InterestCalc.ACCT_LRECL, "DISCGRP", InterestCalc.DISCGRP_LRECL);

    private Main() {}

    public static void main(String[] args) throws IOException {
        PrintStream stdout = new PrintStream(System.out, false, StandardCharsets.ISO_8859_1);
        int rc = run(args, System::getenv, stdout);
        stdout.flush();
        System.exit(rc);
    }

    /** In-process entry point (used by tests). */
    public static int run(String[] args, java.util.function.Function<String, String> env, PrintStream out)
            throws IOException {
        if (args.length < 2) {
            System.err.println("usage: cbact04c <PARM-DATE> <OUT_DIR> [INPUT_DIR]");
            return 2;
        }
        Path outDir = Path.of(args[1]);
        Path inputDir = args.length > 2 ? Path.of(args[2]) : null;
        String dataDir = env.apply("DATA_DIR");
        Path stockDir = Path.of(dataDir != null ? dataDir : "app/data/ASCII");

        Map<String, List<String>> inputs = new HashMap<>();
        for (var dd : STOCK.keySet()) {
            Path file = inputDir != null && Files.exists(inputDir.resolve(dd + ".txt"))
                    ? inputDir.resolve(dd + ".txt") : stockDir.resolve(STOCK.get(dd));
            if (Files.isReadable(file)) {
                inputs.put(dd, RecordFiles.read(file, LRECL.get(dd)));
            }
        }

        InterestCalc.Result r = new InterestCalc(clock(env), out).run(args[0], inputs);
        Files.createDirectories(outDir);
        RecordFiles.write(outDir.resolve("TRANSACT.txt"), r.transact());
        RecordFiles.write(outDir.resolve("ACCTFILE.txt"), r.acctfile());
        return r.rc();
    }

    private static Supplier<LocalDateTime> clock(java.util.function.Function<String, String> env) {
        String v = env.apply("CARDDEMO_CURRENT_DATE");
        if (v == null) {
            v = env.apply("COB_CURRENT_DATE");
        }
        if (v == null) {
            return () -> LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
        int n = v.length() >= 16 ? Integer.parseInt(v.substring(14, 16)) : 0;
        LocalDateTime t = LocalDateTime.of(
                Integer.parseInt(v.substring(0, 4)), Integer.parseInt(v.substring(4, 6)),
                Integer.parseInt(v.substring(6, 8)), Integer.parseInt(v.substring(8, 10)),
                Integer.parseInt(v.substring(10, 12)), Integer.parseInt(v.substring(12, 14)), n * 10_000_000);
        return () -> t;
    }
}
