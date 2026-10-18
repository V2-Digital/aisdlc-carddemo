package carddemo.cbtrn02c;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/** CLI: DD paths come from -D&lt;DD&gt; or env &lt;DD&gt;; clock from CARDDEMO_CURRENT_DATE (YYYYMMDDHHMMSSss). */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run());
    }

    static int run() {
        Map<String, Path> dd = new HashMap<>();
        for (String n : new String[] {"DALYTRAN", "XREFFILE", "ACCTFILE", "TCATBALF", "TRANFILE", "DALYREJS",
                "ACCTFILE_OUT", "TCATBALF_OUT"}) {
            String v = System.getProperty(n, System.getenv(n));
            if (v != null) {
                dd.put(n, Path.of(v));
            }
        }
        String clock = System.getenv("CARDDEMO_CURRENT_DATE");
        if (clock == null || clock.length() < 16) {
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            clock = String.format("%04d%02d%02d%02d%02d%02d%02d", now.getYear(), now.getMonthValue(),
                    now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(),
                    now.getNano() / 10_000_000);
        }
        return new Cbtrn02c(dd, clock, System.out).run();
    }
}
