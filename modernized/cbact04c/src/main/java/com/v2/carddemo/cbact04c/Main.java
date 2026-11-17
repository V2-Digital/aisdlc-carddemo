package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** CLI: {@code --parm <10 chars> [--clock YYYYMMDDHHMMSSss] --in <dir> --out <dir>}. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        System.exit(run(args, System.out));
    }

    static int run(String[] args, java.io.PrintStream stdout) throws IOException {
        String parm = "";
        String clock = null;
        Path in = null;
        Path out = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--parm" -> parm = args[i + 1];
                case "--clock" -> clock = args[i + 1];
                case "--in" -> in = Path.of(args[i + 1]);
                case "--out" -> out = Path.of(args[i + 1]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if (in == null || out == null) {
            System.err.println("usage: --parm <10 chars> [--clock YYYYMMDDHHMMSSss] --in <dir> --out <dir>");
            return 2;
        }
        if (clock == null) {
            LocalDateTime n = LocalDateTime.now(ZoneOffset.UTC);
            clock = String.format("%04d%02d%02d%02d%02d%02d%02d", n.getYear(), n.getMonthValue(),
                    n.getDayOfMonth(), n.getHour(), n.getMinute(), n.getSecond(), n.getNano() / 10_000_000);
        }
        LocalDateTime ts = LocalDateTime.of(Integer.parseInt(clock.substring(0, 4)),
                Integer.parseInt(clock.substring(4, 6)), Integer.parseInt(clock.substring(6, 8)),
                Integer.parseInt(clock.substring(8, 10)), Integer.parseInt(clock.substring(10, 12)),
                Integer.parseInt(clock.substring(12, 14)));
        int hh = Integer.parseInt(clock.substring(14, 16));

        InterestCalc calc = new InterestCalc(parm, ts, hh);
        InterestCalc.Result r;
        try {
            r = calc.run(
                    Records.read(in.resolve("TCATBALF.txt"), InterestCalc.TCATBAL_LRECL),
                    Records.read(in.resolve("XREFFILE.txt"), InterestCalc.XREF_LRECL),
                    Records.read(in.resolve("ACCTFILE.txt"), InterestCalc.ACCT_LRECL),
                    Records.read(in.resolve("DISCGRP.txt"), InterestCalc.DISCGRP_LRECL));
        } catch (Abend a) {
            calc.stdout().forEach(stdout::println);
            return Abend.RC;
        }
        r.stdout().forEach(stdout::println);

        // Atomic: stage to temp files, rename only when both are complete.
        Files.createDirectories(out);
        Path t1 = Files.createTempFile(out, "TRANSACT", ".tmp");
        Path t2 = Files.createTempFile(out, "ACCTFILE", ".tmp");
        try {
            Files.write(t1, Records.write(r.transact(), InterestCalc.TRAN_LRECL));
            Files.write(t2, Records.write(r.acctfile(), InterestCalc.ACCT_LRECL));
            Files.move(t1, out.resolve("TRANSACT.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(t2, out.resolve("ACCTFILE.txt"), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(t1);
            Files.deleteIfExists(t2);
        }
        return 0;
    }
}
