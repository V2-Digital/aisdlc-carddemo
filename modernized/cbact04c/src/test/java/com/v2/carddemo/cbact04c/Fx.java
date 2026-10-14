package com.v2.carddemo.cbact04c;

import java.math.BigDecimal;
import java.util.List;

/** Small fixed-width record builders for in-memory tests. */
final class Fx {
    private Fx() {}

    static String z(String v, int scale, int width) {
        return ZonedDecimal.encode(new BigDecimal(v), scale, width);
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return Records.fit(acct + type + cat + z(bal, 2, 11), 50);
    }

    static String xref(String card, String acct) {
        return Records.fit(card + "         " + acct, 50);
    }

    /** group goes to ACCT-GROUP-ID (112/10). */
    static String acct(String id, String bal, String credit, String debit, String group) {
        String s = id + "Y" + z(bal, 2, 12) + " ".repeat(12 * 3 + 30) // limits + dates (24..77)
                .substring(0, 54) + z(credit, 2, 12) + z(debit, 2, 12) + " ".repeat(10) + Records.fit(group, 10);
        return Records.fit(s, 300);
    }

    static String disc(String group, String type, String cat, String rate) {
        return Records.fit(Records.fit(group, 10) + type + cat + z(rate, 2, 6), 50);
    }

    static Cbact04c.Result run(List<String> tcat, List<String> xref, List<String> acct, List<String> disc) {
        return Cbact04c.run("2022071800", new Cbact04c.Inputs(tcat, xref, acct, disc), () -> "2022071800000000");
    }
}
