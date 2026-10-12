package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;

/** CLI: java -jar cbact04c.jar &lt;PARM&gt; &lt;IN_DIR&gt; &lt;OUT_DIR&gt; */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: cbact04c <PARM> <IN_DIR> <OUT_DIR>");
            System.exit(2);
        }
        Path in = Path.of(args[1]);
        Path outDir = Path.of(args[2]);
        InterestCalc.Result r = InterestCalc.run(args[0], in, clock(System.getenv("CARDDEMO_CURRENT_DATE")));
        StringBuilder sb = new StringBuilder();
        r.sysout().forEach(l -> sb.append(l).append('\n'));
        System.out.print(sb);
        System.out.flush();
        if (r.rc() == 0) {
            Files.createDirectories(outDir);
            FixedFile.write(outDir.resolve("TRANSACT.txt"), r.transact());
            FixedFile.write(outDir.resolve("ACCTFILE.txt"), r.accounts());
        } else {
            System.err.println("CEE3ABD: ABEND U0999 TIMING=0");
        }
        System.exit(r.rc());
    }

    /** CARDDEMO_CURRENT_DATE = YYYYMMDDHHMMSSss, else the system clock in UTC. */
    static Supplier<LocalDateTime> clock(String env) {
        if (env == null || env.length() < 16) {
            return () -> LocalDateTime.now(ZoneOffset.UTC);
        }
        int[] v = new int[7];
        int[] w = {4, 2, 2, 2, 2, 2, 2};
        int p = 0;
        for (int i = 0; i < 7; i++) {
            v[i] = Integer.parseInt(env.substring(p, p + w[i]));
            p += w[i];
        }
        LocalDateTime t = LocalDateTime.of(v[0], v[1], v[2], v[3], v[4], v[5], v[6] * 10_000_000);
        return () -> t;
    }
}
