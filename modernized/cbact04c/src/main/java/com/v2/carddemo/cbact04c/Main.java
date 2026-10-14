package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Supplier;

/** Usage: java -jar cbact04c.jar [PARM]. DDs via DD_&lt;DD&gt; or INPUT_DIR / OUTPUT_DIR. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> env = System.getenv();
        String parm = args.length > 0 ? args[0] : "2022071800";
        DdFiles io = new DdFiles(env, Path.of(env.getOrDefault("INPUT_DIR", ".")),
                Path.of(env.getOrDefault("OUTPUT_DIR", ".")));
        String frozen = env.getOrDefault("CARDDEMO_CURRENT_DATE", env.get("COB_CURRENT_DATE"));
        Supplier<String> clock = frozen != null ? () -> frozen : Main::systemClock;
        System.exit(Cbact04c.run(parm, io, clock, System.out, System.err));
    }

    static String systemClock() {
        LocalDateTime t = LocalDateTime.now(ZoneOffset.UTC);
        return String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }
}
