package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Builds small input directories for unit/characterization tests. */
final class Fx {
    static final String CLOCK = "2022071800000000";
    final Path dir;
    final StringBuilder tcat = new StringBuilder();
    final StringBuilder xref = new StringBuilder();
    final StringBuilder disc = new StringBuilder();
    final StringBuilder acct = new StringBuilder();

    Fx(Path dir) { this.dir = dir; }

    Fx tcat(String acct, String type, String cat, String bal) {
        tcat.append(Records.pad(acct + type + cat + Zoned.encode(new BigDecimal(bal), 11, 2), 50)).append('\n');
        return this;
    }

    Fx xref(String card, String acct) {
        xref.append(Records.pad(card + "0000000000".substring(0, 9) + acct, 50)).append('\n');
        return this;
    }

    Fx disc(String group, String type, String cat, String rate) {
        disc.append(Records.pad(Records.pad(group, 10) + type + cat + Zoned.encode(new BigDecimal(rate), 6, 2), 50)).append('\n');
        return this;
    }

    /** Account with balance and non-zero cycle credit/debit (to prove they get zeroed). */
    Fx acct(String id, String bal) {
        String z = Zoned.encode(new BigDecimal("5.00"), 12, 2);
        String r = id + "Y" + Zoned.encode(new BigDecimal(bal), 12, 2) + Zoned.encode(new BigDecimal("1000.00"), 12, 2)
                + Zoned.encode(new BigDecimal("1000.00"), 12, 2);
        r = Records.pad(r, 78) + z + z + Records.pad("", 20);
        acct.append(Records.pad(r, 300)).append('\n');
        return this;
    }

    Path write() throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("TCATBALF.txt"), tcat.toString());
        Files.writeString(dir.resolve("XREFFILE.txt"), xref.toString());
        Files.writeString(dir.resolve("DISCGRP.txt"), disc.toString());
        Files.writeString(dir.resolve("ACCTFILE.txt"), acct.toString());
        return dir;
    }

    static Cbact04c run(Path in) throws IOException {
        Cbact04c c = new Cbact04c("2022071800", CLOCK);
        c.run(in);
        return c;
    }

    static final int AMT = 132, CARD = 262;
    static BigDecimal amt(String t) { return Zoned.decode(t.substring(AMT, AMT + 11), 2); }
    static BigDecimal bal(String rec) { return Records.acctBal(rec); }
    static String tail(List<String> l, int n) { return String.join("|", l.subList(l.size() - n, l.size())); }
}
