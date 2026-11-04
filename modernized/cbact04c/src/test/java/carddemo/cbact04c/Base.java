package carddemo.cbact04c;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Inline-fixture scenarios shared by AbendTest and DefectTest. */
final class Base {
    private Base() {}

    static final String A1 = "00000000001";
    static final String A2 = "00000000002";

    static Fx.Result run(Path tmp, List<String> tcat, List<String> xref, List<String> acct, List<String> disc)
            throws IOException {
        return Fx.run(Fx.stage(tmp.resolve("in"), tcat, xref, acct, disc), tmp.resolve("out"));
    }

    static String amt(String v) {
        return Zoned.encode(new java.math.BigDecimal(v), 9);
    }

    static String bal(String v) {
        return Zoned.encode(new java.math.BigDecimal(v), 10);
    }

    static String acct(String id, String balance) {
        return Fx.acct(id, bal(balance), "00000000500{", "00000000100}", "");
    }
}
