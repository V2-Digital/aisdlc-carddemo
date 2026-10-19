package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Small in-memory fixtures and a runner. */
final class Fx {
    static final Clock CLOCK = Main.clock("2022071800000000");

    static Path repo() { return Path.of(System.getProperty("repo.root")).normalize(); }

    static String acct(String id, long bal, long cred, long deb, String group) {
        return Records.pad(id + "Y" + Zoned.encode(bal, 12) + "X".repeat(12 * 2 + 30) + Zoned.encode(cred, 12)
                + Zoned.encode(deb, 12) + "ZIPZIPZIP1" + Records.pad(group, 10) + "FILLERFILLER", 300);
    }
    static String tcat(String id, String type, String cat, long bal) {
        return Records.pad(id + type + cat + Zoned.encode(bal, 11), 50);
    }
    static String xref(String card, String id) { return Records.pad(card + "         " + id, 36); }
    static String disc(String group, String type, String cat, long rate) {
        return Records.pad(Records.pad(group, 10) + type + cat + Zoned.encode(rate, 6), 50);
    }

    record Result(InterestCalc calc, List<String> out, AbendException abend) {}

    static Result run(List<String> tcat, List<String> xref, List<String> disc, List<String> accts) {
        List<String> out = new ArrayList<>();
        List<String> x = new ArrayList<>();
        for (String r : xref) x.add(Records.pad(r, 50));
        InterestCalc c = new InterestCalc("2022071800", CLOCK, out::add, tcat, x, disc, accts);
        try { c.run(); return new Result(c, out, null); } catch (AbendException e) { return new Result(c, out, e); }
    }
}
