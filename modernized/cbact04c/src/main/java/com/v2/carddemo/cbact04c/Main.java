package com.v2.carddemo.cbact04c;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * {@code java -jar cbact04c.jar <PARM-DATE> <IN_DIR> <OUT_DIR>}
 * Inputs: {@code <IN_DIR>/<DD>.txt} (or the stock names tcatbal/cardxref/acctdata/discgrp.txt), overridable per DD
 * with env {@code CARDDEMO_PATH_<DD>}. Clock: env {@code CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss}, else system (UTC).
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: cbact04c <PARM-DATE> <IN_DIR> <OUT_DIR>");
            System.exit(2);
        }
        Path in = Path.of(args[1]);
        Path out = Path.of(args[2]);
        Files.createDirectories(out);
        InterestCalc calc = new InterestCalc(args[0], clock(System.getenv("CARDDEMO_CURRENT_DATE")),
                System.out::println,
                FileStore.read(input(in, "TCATBALF", "tcatbal"), Records.TCATBAL_LEN),
                FileStore.read(input(in, "XREFFILE", "cardxref"), Records.XREF_LEN),
                FileStore.read(input(in, "DISCGRP", "discgrp"), Records.DISC_LEN),
                FileStore.read(input(in, "ACCTFILE", "acctdata"), Records.ACCT_LEN));
        int rc = 0;
        try {
            calc.run();
        } catch (AbendException e) {
            rc = 16;
        }
        FileStore.write(out.resolve("TRANSACT.txt"), calc.transactions);
        FileStore.write(out.resolve("ACCTFILE.txt"), calc.accounts);
        System.exit(rc);
    }

    static Path input(Path dir, String dd, String stockName) {
        String override = System.getenv("CARDDEMO_PATH_" + dd);
        if (override != null) {
            return Path.of(override);
        }
        Path p = dir.resolve(dd + ".txt");
        return Files.exists(p) ? p : dir.resolve(stockName + ".txt");
    }

    static Clock clock(String spec) {
        if (spec == null || spec.isEmpty()) {
            return Clock.systemUTC();
        }
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(spec.substring(0, 4)),
                Integer.parseInt(spec.substring(4, 6)), Integer.parseInt(spec.substring(6, 8)),
                Integer.parseInt(spec.substring(8, 10)), Integer.parseInt(spec.substring(10, 12)),
                Integer.parseInt(spec.substring(12, 14)), Integer.parseInt(spec.substring(14, 16)) * 10_000_000);
        return Clock.fixed(Instant.from(t.atOffset(ZoneOffset.UTC)), ZoneOffset.UTC);
    }
}
